package com.par9uet.jm.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

suspend fun checkPreferredApi(
    current: String,
    alternatives: List<String>,
    check: suspend (String) -> ApiLineCheck,
): List<ApiLineCheck> {
    val preferred = check(current)
    if (preferred.isAvailable) return listOf(preferred)
    return coroutineScope {
        listOf(preferred) + alternatives.distinct().filterNot { it == current }
            .map { async { check(it) } }.awaitAll()
    }
}
