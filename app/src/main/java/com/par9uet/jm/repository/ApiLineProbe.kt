package com.par9uet.jm.repository

import com.par9uet.jm.retrofit.API_TOKEN_HASH
import com.par9uet.jm.retrofit.API_TS
import com.par9uet.jm.retrofit.API_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.time.TimeSource

data class ApiLineCheck(
    val api: String,
    val isAvailable: Boolean,
    val latencyMs: Long? = null,
)

class ApiLineProbe {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    suspend fun check(api: String): ApiLineCheck = withContext(Dispatchers.IO) {
        val baseUrl = api.toHttpUrlOrNull()
            ?: return@withContext ApiLineCheck(api = api, isAvailable = false)
        val settingUrl = baseUrl.newBuilder().encodedPath("/setting").build()
        val request = Request.Builder()
            .url(settingUrl)
            .get()
            .header("tokenparam", "$API_TS,$API_VERSION")
            .header("token", API_TOKEN_HASH)
            .build()
        val startedAt = TimeSource.Monotonic.markNow()
        runCatching {
            client.newCall(request).execute().use { response ->
                val isJson = response.body.contentType()?.subtype?.contains("json") == true
                val body = response.body.string()
                val isApiResponse = Regex("""\"code\"\s*:\s*200""").containsMatchIn(body)
                ApiLineCheck(
                    api = api,
                    isAvailable = response.isSuccessful && isJson && isApiResponse,
                    latencyMs = startedAt.elapsedNow().inWholeMilliseconds,
                )
            }
        }.getOrElse {
            ApiLineCheck(api = api, isAvailable = false)
        }
    }
}

object ApiLineSelector {
    fun choose(currentApi: String, checks: List<ApiLineCheck>): String? {
        if (checks.any { it.api == currentApi && it.isAvailable }) {
            return currentApi
        }
        return checks.asSequence()
            .filter { it.isAvailable && it.latencyMs != null }
            .minByOrNull { it.latencyMs!! }
            ?.api
    }
}
