package com.par9uet.jm.store

import com.par9uet.jm.data.models.LocalSetting
import com.par9uet.jm.storage.LocalSettingStorage
import com.par9uet.jm.repository.SourceDomainResolver
import com.par9uet.jm.repository.ApiLineCheck
import com.par9uet.jm.repository.ApiLineProbe
import com.par9uet.jm.repository.ApiLineSelector
import com.par9uet.jm.task.AppInitTask
import com.par9uet.jm.task.AppTaskInfo
import com.par9uet.jm.utils.log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalSettingManager(
    private val localSettingStorage: LocalSettingStorage,
    private val sourceDomainResolver: SourceDomainResolver,
    private val apiLineProbe: ApiLineProbe,
    private val scope: CoroutineScope,
) : AppInitTask {
    private val _localSettingState = MutableStateFlow(LocalSetting())
    val localSettingState = _localSettingState.asStateFlow()
    private val sourceWebsiteRefreshMutex = Mutex()
    private val _isSourceWebsiteRefreshing = MutableStateFlow(false)
    val isSourceWebsiteRefreshing = _isSourceWebsiteRefreshing.asStateFlow()
    private val apiLineRefreshMutex = Mutex()
    private val _apiLineChecks = MutableStateFlow<List<ApiLineCheck>>(emptyList())
    val apiLineChecks = _apiLineChecks.asStateFlow()
    private val _isApiLineRefreshing = MutableStateFlow(false)
    val isApiLineRefreshing = _isApiLineRefreshing.asStateFlow()

    suspend fun refreshApiLines(): Boolean = apiLineRefreshMutex.withLock {
        _isApiLineRefreshing.value = true
        try {
            val checks = coroutineScope {
                _localSettingState.value.apiList.distinct().map { api ->
                    async(Dispatchers.IO) { apiLineProbe.check(api) }
                }.awaitAll()
            }
            _apiLineChecks.value = checks
            val selectedApi = ApiLineSelector.choose(_localSettingState.value.api, checks)
                ?: return@withLock false
            if (selectedApi != _localSettingState.value.api) {
                updateApi(selectedApi)
                log("当前 API 不可用，已自动切换至：$selectedApi")
            }
            true
        } finally {
            _isApiLineRefreshing.value = false
        }
    }

    suspend fun refreshSourceWebsite(): Boolean = sourceWebsiteRefreshMutex.withLock {
        _isSourceWebsiteRefreshing.value = true
        try {
            val website = sourceDomainResolver.resolve() ?: return@withLock false
            if (_localSettingState.value.sourceWebsite != website) {
                _localSettingState.update { it.copy(sourceWebsite = website) }
                localSettingStorage.set(_localSettingState.value)
                log("已切换内容站点域名：$website")
            }
            true
        } catch (e: Exception) {
            log("自动检索内容站点域名失败：${e.message}")
            false
        } finally {
            _isSourceWebsiteRefreshing.value = false
        }
    }

    fun updateApi(api: String) {
        _localSettingState.update {
            it.copy(
                api = api
            )
        }
        localSettingStorage.set(_localSettingState.value)
    }

    fun updateTheme(theme: String) {
        _localSettingState.update {
            it.copy(
                theme = theme
            )
        }
        localSettingStorage.set(_localSettingState.value)
    }

    fun updateColorPalette(colorPalette: String) {
        _localSettingState.update {
            it.copy(
                colorPalette = colorPalette
            )
        }
        localSettingStorage.set(_localSettingState.value)
    }

    fun updateShunt(shunt: String) {
        _localSettingState.update {
            it.copy(
                shunt = shunt
            )
        }
        localSettingStorage.set(_localSettingState.value)
    }

    fun updatePrefetchCount(prefetchCount: Int) {
        _localSettingState.update {
            it.copy(
                prefetchCount = prefetchCount
            )
        }
        localSettingStorage.set(_localSettingState.value)
    }

    fun updateReadMode(readMode: String) {
        _localSettingState.update {
            it.copy(
                readMode = readMode
            )
        }
        localSettingStorage.set(_localSettingState.value)
    }

    fun closeShowComicScrollReadTip() {
        _localSettingState.update {
            it.copy(
                showComicScrollReadTip = false
            )
        }
        localSettingStorage.set(_localSettingState.value)
    }

    fun closeShowComicPageReadTip() {
        _localSettingState.update {
            it.copy(
                showComicPageReadTip = false
            )
        }
        localSettingStorage.set(_localSettingState.value)
    }

    fun updateBrightness(brightness: Float) {
        _localSettingState.update {
            it.copy(
                brightness = brightness
            )
        }
        localSettingStorage.set(_localSettingState.value)
    }

    fun updateBrightnessFollowSystem(brightnessFollowSystem: Boolean) {
        _localSettingState.update {
            it.copy(
                brightnessFollowSystem = brightnessFollowSystem
            )
        }
        localSettingStorage.set(_localSettingState.value)
    }

    fun updateShowPageNumber(showPageNumber: Boolean) {
        _localSettingState.update {
            it.copy(
                showPageNumber = showPageNumber
            )
        }
        localSettingStorage.set(_localSettingState.value)
    }

    fun updateNoLockScreen(noLockScreen: Boolean) {
        _localSettingState.update {
            it.copy(
                noLockScreen = noLockScreen
            )
        }
        localSettingStorage.set(_localSettingState.value)
    }

    fun updateSupportZoom(supportZoom: Boolean) {
        _localSettingState.update {
            it.copy(
                supportZoom = supportZoom
            )
        }
        localSettingStorage.set(_localSettingState.value)
    }

    private var appTaskInfo = AppTaskInfo(
        taskName = "加载本地 APP 设置",
        sort = 2,
    )

    override suspend fun init() {
        log("本地应用设置开始初始化")
        log("加载本地应用设置")
        _localSettingState.update {
            localSettingStorage.get()
        }
        log("已加载本地应用设置")
        refreshApiLines()
        scope.launch(Dispatchers.IO) {
            refreshSourceWebsite()
        }
        log("本地应用设置初始化结束")
    }

    override fun getAppTaskInfo(): AppTaskInfo = appTaskInfo
}
