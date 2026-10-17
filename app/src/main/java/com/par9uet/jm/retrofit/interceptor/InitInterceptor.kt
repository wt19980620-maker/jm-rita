package com.par9uet.jm.retrofit.interceptor

import com.par9uet.jm.store.InitManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class InitInterceptor(
    private val initManager: InitManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // 初始化阶段只恢复本地状态，登录和远端配置请求也需等待 Cookie 恢复。
        if (!initManager.deferred.isCompleted) {
            runBlocking {
                initManager.deferred.await()
            }
        }
        return chain.proceed(chain.request())
    }
}
