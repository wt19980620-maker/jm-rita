package com.par9uet.jm.repository

import com.google.gson.Gson
import com.par9uet.jm.config.VIDEO_SOURCE_API
import com.par9uet.jm.config.VIDEO_PLAYBACK_SOURCE_WEBSITE
import com.par9uet.jm.config.VIDEO_SOURCE_WEBSITE
import com.par9uet.jm.data.models.PornhubPlayback
import com.par9uet.jm.data.models.PornhubVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

private data class PornhubSearchResponse(
    val videos: List<PornhubVideo>? = null,
)

class PornhubVideoRepository(
    private val gson: Gson,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun search(
        query: String,
        ordering: String,
        page: Int,
    ): List<PornhubVideo> = withContext(Dispatchers.IO) {
        val url = "$VIDEO_SOURCE_API/search".toHttpUrl().newBuilder()
            .addQueryParameter("ordering", ordering)
            .addQueryParameter("period", "weekly")
            .addQueryParameter("thumbsize", "large")
            .addQueryParameter("page", page.toString())
            .apply {
                query.trim().takeIf(String::isNotEmpty)?.let {
                    addQueryParameter("search", it)
                }
            }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "JM-RITA-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("视频源请求失败：${response.code}")
            }
            gson.fromJson(response.body.string(), PornhubSearchResponse::class.java)
                .videos
                .orEmpty()
                .filter { it.video_id.isNotBlank() && it.title.isNotBlank() }
        }
    }

    suspend fun resolvePlayback(videoId: String): PornhubPlayback = withContext(Dispatchers.IO) {
        val pageUrl = PornhubVideoUrl.playbackPage(videoId)
        val pageRequest = browserRequest(pageUrl)
        val html = client.newCall(pageRequest).execute().use { response ->
            response.requireBody("视频详情页请求失败")
        }
        val initialDefinitions = PornhubMediaParser.parsePage(html)
        val resolvedDefinitions = initialDefinitions.flatMap { definition ->
            if (definition.url.contains("/video/get_media", ignoreCase = true)) {
                resolveMediaEndpoint(definition.url, pageUrl).ifEmpty { listOf(definition) }
            } else {
                listOf(definition)
            }
        }
        val streams = PornhubMediaParser.toStreams(resolvedDefinitions)
            .filterNot { it.url.contains("/video/get_media", ignoreCase = true) }
        if (streams.isEmpty()) {
            throw IOException("没有解析到可播放地址，可能是页面验证或临时链接已失效")
        }
        PornhubPlayback(pageUrl = pageUrl, streams = streams)
    }

    private fun resolveMediaEndpoint(url: String, pageUrl: String): List<PornhubMediaDefinition> {
        val request = browserRequest(url)
            .newBuilder()
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Referer", pageUrl)
            .header("X-Requested-With", "XMLHttpRequest")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use emptyList()
            PornhubMediaParser.parseMediaResponse(response.body.string())
        }
    }

    private fun browserRequest(url: String): Request = Request.Builder()
        .url(url)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/138.0.0.0 Mobile Safari/537.36",
        )
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .header("Cookie", "accessAgeDisclaimerPH=1")
        .build()

    private fun Response.requireBody(message: String): String {
        if (!isSuccessful) throw IOException("$message：$code")
        return body.string().takeIf(String::isNotBlank)
            ?: throw IOException("$message：响应为空")
    }
}

object PornhubVideoUrl {
    fun officialPage(videoId: String): String = "$VIDEO_SOURCE_WEBSITE/view_video.php"
        .toHttpUrl()
        .newBuilder()
        .addQueryParameter("viewkey", videoId.trim())
        .build()
        .toString()

    fun playbackPage(videoId: String): String = "$VIDEO_PLAYBACK_SOURCE_WEBSITE/view_video.php"
        .toHttpUrl()
        .newBuilder()
        .addQueryParameter("viewkey", videoId.trim())
        .build()
        .toString()
}
