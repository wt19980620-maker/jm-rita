package com.par9uet.jm.repository

import com.par9uet.jm.retrofit.API_TOKEN_HASH
import com.par9uet.jm.retrofit.API_TS
import com.par9uet.jm.retrofit.API_VERSION
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.time.TimeSource

data class ApiLineCheck(
    val api: String,
    val isAvailable: Boolean,
    val latencyMs: Long? = null,
)

class ApiLineProbe(private val client: Call.Factory) {
    suspend fun check(api: String): ApiLineCheck {
        val baseUrl = api.toHttpUrlOrNull()
            ?: return ApiLineCheck(api = api, isAvailable = false)
        val settingUrl = baseUrl.newBuilder().encodedPath("/setting").build()
        val request = Request.Builder()
            .url(settingUrl)
            .get()
            .header("tokenparam", "$API_TS,$API_VERSION")
            .header("token", API_TOKEN_HASH)
            .build()
        val startedAt = TimeSource.Monotonic.markNow()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(ApiLineCheck(api, false))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!continuation.isActive) return
                        val available = runCatching {
                            val body = response.body
                            response.isSuccessful &&
                                body.contentType()?.subtype?.contains("json") == true &&
                                !body.source().request(256L * 1024 + 1) &&
                                JsonParser.parseString(body.string()).asJsonObject["code"]?.asInt == 200
                        }.getOrDefault(false)
                        continuation.resume(ApiLineCheck(api, available, startedAt.elapsedNow().inWholeMilliseconds))
                    }
                }
            })
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
