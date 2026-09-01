package com.par9uet.jm.repository

import com.google.gson.Gson
import com.par9uet.jm.config.GITHUB_LATEST_APK_URL
import com.par9uet.jm.config.GITHUB_LATEST_RELEASE_API
import com.par9uet.jm.config.GITHUB_LATEST_RELEASE_PAGE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AppRelease(
    val tagName: String,
    val versionName: String,
    val apkName: String,
    val apkUrl: String,
    val releaseUrl: String,
    val notes: String,
)

private data class GitHubReleaseResponse(
    val tag_name: String,
    val html_url: String,
    val body: String?,
    val assets: List<GitHubReleaseAsset>,
)

private data class GitHubReleaseAsset(
    val name: String,
    val browser_download_url: String,
)

class GitHubUpdateRepository(
    private val gson: Gson,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun getLatestRelease(): AppRelease = withContext(Dispatchers.IO) {
        runCatching { getLatestReleaseFromApi() }
            .getOrElse { getLatestReleaseFromRedirect() }
    }

    private fun getLatestReleaseFromApi(): AppRelease {
        val request = Request.Builder()
            .url(GITHUB_LATEST_RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "JM-RITA-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub 请求失败：${response.code}")
            }
            val release = gson.fromJson(response.body.string(), GitHubReleaseResponse::class.java)
            val apk = release.assets.firstOrNull { it.name == "jm-rita-latest.apk" }
                ?: release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: throw IOException("最新版 Release 中没有 APK 文件")
            return AppRelease(
                tagName = release.tag_name,
                versionName = release.tag_name.removePrefix("v"),
                apkName = apk.name,
                apkUrl = apk.browser_download_url,
                releaseUrl = release.html_url,
                notes = release.body.orEmpty(),
            )
        }
    }

    private fun getLatestReleaseFromRedirect(): AppRelease {
        val request = Request.Builder()
            .url(GITHUB_LATEST_RELEASE_PAGE)
            .header("User-Agent", "JM-RITA-Android")
            .head()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub 请求失败：${response.code}")
            }
            val releaseUrl = response.request.url.toString()
            val tagName = response.request.url.pathSegments.lastOrNull()
                ?.takeIf { it.startsWith("v") }
                ?: throw IOException("无法识别 GitHub 最新版本")
            return AppRelease(
                tagName = tagName,
                versionName = tagName.removePrefix("v"),
                apkName = "jm-rita-latest.apk",
                apkUrl = GITHUB_LATEST_APK_URL,
                releaseUrl = releaseUrl,
                notes = "",
            )
        }
    }
}

object VersionComparator {
    fun isNewer(latest: String, current: String): Boolean {
        val latestParts = versionParts(latest)
        val currentParts = versionParts(current)
        val size = maxOf(latestParts.size, currentParts.size)
        return (0 until size).firstNotNullOfOrNull { index ->
            val latestPart = latestParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            when {
                latestPart > currentPart -> true
                latestPart < currentPart -> false
                else -> null
            }
        } ?: false
    }

    private fun versionParts(version: String): List<Int> = version
        .removePrefix("v")
        .split(Regex("[^0-9]+"))
        .filter { it.isNotBlank() }
        .mapNotNull(String::toIntOrNull)
}
