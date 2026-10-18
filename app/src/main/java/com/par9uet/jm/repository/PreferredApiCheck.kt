package com.par9uet.jm.repository

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

suspend fun checkPreferredApi(
    current: String,
    alternatives: List<String>,
    check: suspend (String) -> ApiLineCheck,
): List<ApiLineCheck> {
    val preferred = check(current)
    if (preferred.isAvailable) return listOf(preferred)
    return listOf(preferred) + checkAlternativeApis(current, alternatives, check)
}

internal suspend fun checkAlternativeApis(
    failedApi: String,
    alternatives: List<String>,
    check: suspend (String) -> ApiLineCheck,
): List<ApiLineCheck> = coroutineScope {
    val candidates = alternatives.distinct().filterNot { it == failedApi }
    val results = Channel<ApiLineCheck>(candidates.size.coerceAtLeast(1))
    val jobs = candidates.map { api -> launch { results.send(check(api)) } }
    val completed = mutableListOf<ApiLineCheck>()
    try {
        for (index in candidates.indices) {
            val result = results.receive()
            completed.add(result)
            if (result.isAvailable) break
        }
        completed
    } finally {
        jobs.forEach { it.cancel() }
        results.cancel()
    }
}
