package com.par9uet.jm.repository

import java.io.IOException

internal fun <T> requestWithApiFallback(
    method: String,
    currentApi: () -> String,
    recoverApi: (String) -> Unit,
    isCanceled: () -> Boolean,
    isRouteFailure: (T) -> Boolean = { false },
    closeFailedResponse: (T) -> Unit = {},
    execute: (String) -> T,
): T {
    val initialApi = currentApi()
    fun fallbackApi(): String? {
        if (method != "GET" || isCanceled()) return null
        if (currentApi() == initialApi) recoverApi(initialApi)
        return currentApi().takeIf { it != initialApi && !isCanceled() }
    }
    val response = try {
        execute(initialApi)
    } catch (failure: IOException) {
        val fallback = fallbackApi() ?: throw failure
        return execute(fallback)
    }
    if (!isRouteFailure(response)) return response
    val fallback = try {
        fallbackApi()
    } catch (error: Exception) {
        closeFailedResponse(response)
        throw error
    } ?: return response
    closeFailedResponse(response)
    return execute(fallback)
}
