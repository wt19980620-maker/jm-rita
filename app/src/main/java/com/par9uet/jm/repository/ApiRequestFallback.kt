package com.par9uet.jm.repository

import java.io.IOException

internal fun <T> requestWithApiFallback(
    method: String,
    currentApi: () -> String,
    awaitLineCheck: () -> Unit,
    isCanceled: () -> Boolean,
    execute: (String) -> T,
): T {
    val initialApi = currentApi()
    try {
        return execute(initialApi)
    } catch (failure: IOException) {
        if (method != "GET" || isCanceled()) throw failure
        awaitLineCheck()
        val fallback = currentApi()
        if (fallback == initialApi || isCanceled()) throw failure
        return execute(fallback)
    }
}
