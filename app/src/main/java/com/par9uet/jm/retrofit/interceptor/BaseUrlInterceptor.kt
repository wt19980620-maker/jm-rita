package com.par9uet.jm.retrofit.interceptor

import com.par9uet.jm.store.LocalSettingManager
import com.par9uet.jm.repository.requestWithApiFallback
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

class BaseUrlInterceptor(
    private val localSettingManager: LocalSettingManager
) : Interceptor {

    private fun getBaseUrl() = localSettingManager.localSettingState.value.api

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return requestWithApiFallback(
            method = request.method,
            currentApi = ::getBaseUrl,
            recoverApi = { failedApi ->
                runBlocking { localSettingManager.recoverApi(failedApi) { chain.call().isCanceled() } }
            },
            isCanceled = { chain.call().isCanceled() },
            isRouteFailure = { it.code in listOf(502, 503, 504) },
            closeFailedResponse = { it.close() },
        ) { api ->
            val baseUrl = api.toHttpUrl()
            val newUrl = request.url.newBuilder()
                .scheme(baseUrl.scheme)
                .host(baseUrl.host)
                .port(baseUrl.port)
                .build()
            chain.proceed(request.newBuilder().url(newUrl).build())
        }
    }
}
