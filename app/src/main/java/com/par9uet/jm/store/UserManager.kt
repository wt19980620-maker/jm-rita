package com.par9uet.jm.store

import com.par9uet.jm.data.models.User
import com.par9uet.jm.repository.UserRepository
import com.par9uet.jm.retrofit.Retrofit
import com.par9uet.jm.retrofit.model.LoginResponse
import com.par9uet.jm.retrofit.model.NetWorkResult
import com.par9uet.jm.storage.CookieStorage
import com.par9uet.jm.storage.UserStorage
import com.par9uet.jm.task.AppInitTask
import com.par9uet.jm.task.AppTaskInfo
import com.par9uet.jm.ui.models.CommonUIState
import com.par9uet.jm.utils.log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.atomic.AtomicLong

class UserManager(
    private val userStorage: UserStorage,
    private val cookieStorage: CookieStorage,
    private val userRepository: UserRepository,
    private val retrofit: Retrofit
) : AppInitTask {
    private val _userState = MutableStateFlow(CommonUIState<User>())
    val userState = _userState.asStateFlow()
    private val loginRevision = AtomicLong()
    @Volatile private var loginJob: Job? = null

    suspend fun stopAutoLogin(): Long = withContext(Dispatchers.Main.immediate) {
        val revision = loginRevision.incrementAndGet()
        val previous = loginJob
        previous?.cancelAndJoin()
        if (loginRevision.get() == revision) {
            loginJob = null
            _userState.update { it.copy(isLoading = false) }
        }
        revision
    }

    val isLoginState = _userState.map { (it.data?.id ?: 0) > 0 }

    private val appTaskInfo = AppTaskInfo(
        taskName = "加载上次退出前保存的用户信息",
        sort = 4,
    )

    fun updateUser(user: User) {
        loginRevision.incrementAndGet()
        loginJob?.cancel()
        _userState.update {
            it.copy(
                data = user,
                isLoading = false,
            )
        }
        userStorage.set(user)
    }

    fun clearUser() {
        loginRevision.incrementAndGet()
        loginJob?.cancel()
        _userState.update {
            it.copy(
                data = User.create(),
                isLoading = false,
            )
        }
        retrofit.clearCookie()
        userStorage.remove()
        cookieStorage.remove()
    }

    suspend fun autoLogin(
        username: String,
        password: String,
        keepCachedUser: Boolean = false,
        expectedRevision: Long? = null,
    ) = withContext(Dispatchers.Main.immediate) {
        if (expectedRevision != null && loginRevision.get() != expectedRevision) return@withContext
        val revision = stopAutoLogin()
        if (loginRevision.get() != revision) return@withContext
        loginJob = currentCoroutineContext()[Job]
        _userState.update {
            it.copy(
                isLoading = true,
                isError = false,
                errorMsg = ""
            )
        }
        try {
            val data = userRepository.login(username, password)
            currentCoroutineContext().ensureActive()
            if (loginRevision.get() != revision) return@withContext
            when (data) {
                is NetWorkResult.Error -> {
                    _userState.update {
                        it.copy(
                            isError = true,
                            errorMsg = data.message,
                            data = if (keepCachedUser) it.data else User.create()
                        )
                    }
                }

                is NetWorkResult.Success<LoginResponse> -> {
                    _userState.update {
                        it.copy(
                            data = data.data.toUser(
                                password = password
                            )
                        )
                    }
                    _userState.value.data?.let(userStorage::set)
                }
            }
        } finally {
            if (loginRevision.get() == revision) {
                loginJob = null
                _userState.update { it.copy(isLoading = false) }
            }
        }
    }

    override suspend fun init() {
        log("用户信息开始初始化")
        log("加载本地用户、cookie、登录信息")
        _userState.update {
            it.copy(
                data = userStorage.get()
            )
        }
        log("已加载本地用户、cookie、登录信息")
        log("用户信息初始化结束")
    }

    override suspend fun refresh() = supervisorScope {
        if (loginRevision.get() != 0L) return@supervisorScope
        val user = _userState.value.data ?: return@supervisorScope
        if (user.username.isNotEmpty() && user.password.isNotEmpty()) {
            log("检测到已保存了用户登录信息，开始执行一次用户登录")
            launch {
                autoLogin(user.username, user.password, keepCachedUser = true, expectedRevision = 0L)
            }.join()
        }
    }

    override fun getAppTaskInfo(): AppTaskInfo = appTaskInfo
}
