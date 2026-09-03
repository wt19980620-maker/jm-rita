package com.par9uet.jm.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.par9uet.jm.config.ANIME_DEFAULT_SOURCE_NAME
import com.par9uet.jm.config.ANIME_DEFAULT_SOURCE_PACKAGE
import com.par9uet.jm.config.ANIME_DEFAULT_SOURCE_URL
import com.par9uet.jm.config.ANIME_SOURCE_INDEX
import com.par9uet.jm.data.models.Anime
import com.par9uet.jm.data.models.AnimeDetails
import com.par9uet.jm.data.models.AnimeEpisode
import com.par9uet.jm.data.models.AnimePlayback
import com.par9uet.jm.data.models.AnimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.concurrent.TimeUnit

private data class AnimeRepoEntry(
    @SerializedName("pkg")
    val pkg: String = "",
    @SerializedName("version")
    val version: String = "",
    @SerializedName("nsfw")
    val nsfw: Int = 0,
    @SerializedName("sources")
    val sources: List<AnimeRepoSource> = emptyList(),
)

private data class AnimeRepoSource(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("lang")
    val lang: String = "",
    @SerializedName("baseUrl")
    val baseUrl: String = "",
)

data class AnimePage(
    val items: List<Anime>,
    val hasMore: Boolean,
)

class AnimeRepository(
    context: Context,
    private val gson: Gson,
) {
    private val preferences = context.getSharedPreferences("anime_sources", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun syncSources(force: Boolean = false): List<AnimeSource> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cachedJson = preferences.getString(KEY_CATALOG, null)
        val lastSync = preferences.getLong(KEY_LAST_SYNC, 0L)
        val json = if (!force && cachedJson != null && now - lastSync < SYNC_INTERVAL_MS) {
            cachedJson
        } else {
            runCatching { request(ANIME_SOURCE_INDEX) }
                .onSuccess {
                    preferences.edit()
                        .putString(KEY_CATALOG, it)
                        .putLong(KEY_LAST_SYNC, now)
                        .apply()
                }
                .getOrElse { cachedJson }
        }
        parseSupportedSources(json).ifEmpty { listOf(defaultSource()) }
    }

    suspend fun loadAnime(source: AnimeSource, query: String, page: Int): AnimePage =
        withContext(Dispatchers.IO) {
            requireSupported(source)
            val url = if (query.isBlank()) {
                source.baseUrl.toHttpUrl().newBuilder()
                    .addPathSegments("index.php/vod/show/id/5")
                    .apply { if (page > 1) addPathSegments("page/$page") }
                    .build()
            } else {
                source.baseUrl.toHttpUrl().newBuilder()
                    .addPathSegments("index.php/vod/search")
                    .apply {
                        if (page > 1) {
                            addPathSegment("page")
                            addPathSegment(page.toString())
                            addPathSegment("wd")
                            addPathSegment(query.trim())
                        } else {
                            addQueryParameter("wd", query.trim())
                        }
                    }
                    .build()
            }
            val document = Jsoup.parse(request(url.toString()), url.toString())
            val selector = if (query.isBlank()) ".myui-vodlist__box" else "#searchList li"
            val items = document.select(selector).mapNotNull { element ->
                val thumb = element.selectFirst("a.myui-vodlist__thumb") ?: return@mapNotNull null
                val title = thumb.attr("title").ifBlank { thumb.text() }.trim()
                val detailUrl = thumb.absoluteUrl("href", source.baseUrl)
                if (title.isBlank() || detailUrl.isBlank()) return@mapNotNull null
                Anime(
                    title = title,
                    url = detailUrl,
                    thumbnailUrl = thumb.absoluteUrl("data-original", source.baseUrl)
                        .ifBlank { thumb.absoluteUrl("data-src", source.baseUrl) },
                )
            }.distinctBy(Anime::url)
            AnimePage(
                items = items,
                hasMore = document.selectFirst(".myui-page a:contains(下一页)") != null,
            )
        }

    suspend fun loadDetails(source: AnimeSource, anime: Anime): AnimeDetails =
        withContext(Dispatchers.IO) {
            requireSupported(source)
            val document = Jsoup.parse(request(anime.url), anime.url)
            val resolvedAnime = anime.copy(
                title = document.selectFirst(".myui-content__detail .title")?.text()
                    ?.takeIf(String::isNotBlank) ?: anime.title,
                thumbnailUrl = document.selectFirst(".myui-vodlist__thumb.picture img")
                    ?.absoluteUrl("data-original", source.baseUrl)
                    ?.takeIf(String::isNotBlank) ?: anime.thumbnailUrl,
            )
            val episodes = document.select("#playlist1 ul li a").mapNotNull { link ->
                val url = link.absoluteUrl("href", source.baseUrl)
                if (url.isBlank()) null else AnimeEpisode(
                    name = link.attr("title").ifBlank { link.text() }.trim(),
                    url = url,
                )
            }.distinctBy(AnimeEpisode::url).reversed()
            if (episodes.isEmpty()) throw IOException("该动漫暂时没有可播放剧集")
            AnimeDetails(
                anime = resolvedAnime,
                description = document.selectFirst("p.data:contains(简介：)")?.ownText().orEmpty(),
                episodes = episodes,
            )
        }

    suspend fun resolvePlayback(source: AnimeSource, episode: AnimeEpisode): AnimePlayback =
        withContext(Dispatchers.IO) {
            requireSupported(source)
            val html = request(episode.url, referer = source.baseUrl)
            val streamUrl = XiaoxintvParser.extractStreamUrl(html, gson)
            if (!streamUrl.startsWith("https://")) {
                throw IOException("视频源返回了不安全或无效的播放地址")
            }
            AnimePlayback(
                pageUrl = episode.url,
                streamUrl = streamUrl,
                referer = source.baseUrl,
            )
        }

    private fun parseSupportedSources(json: String?): List<AnimeSource> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<AnimeRepoEntry>>() {}.type
        val entries = runCatching {
            gson.fromJson<List<AnimeRepoEntry>>(json, type)
        }.getOrDefault(emptyList())
        return entries.asSequence()
            .filter { it.pkg in SUPPORTED_PACKAGES && it.nsfw == 0 }
            .mapNotNull { entry ->
                val source = entry.sources.firstOrNull {
                    it.lang.startsWith("zh") && it.baseUrl.startsWith("https://")
                } ?: return@mapNotNull null
                AnimeSource(entry.pkg, source.name, entry.version, source.baseUrl.removeSuffix("/"))
            }
            .toList()
    }

    private fun requireSupported(source: AnimeSource) {
        if (source.packageName !in SUPPORTED_PACKAGES || !source.baseUrl.startsWith("https://")) {
            throw IOException("该动漫源尚未适配")
        }
    }

    private fun request(url: String, referer: String? = null): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
        referer?.let { builder.header("Referer", it) }
        return client.newCall(builder.build()).execute().use { response ->
            response.requireBody("动漫源请求失败")
        }
    }

    private fun Response.requireBody(message: String): String {
        if (!isSuccessful) throw IOException("$message：$code")
        return body.string().takeIf(String::isNotBlank) ?: throw IOException("$message：响应为空")
    }

    private fun Element.absoluteUrl(attribute: String, baseUrl: String): String {
        val value = attr(attribute).trim()
        if (value.isBlank()) return ""
        return runCatching { baseUrl.toHttpUrl().resolve(value)?.toString() }.getOrNull().orEmpty()
    }

    private fun defaultSource() = AnimeSource(
        packageName = ANIME_DEFAULT_SOURCE_PACKAGE,
        name = ANIME_DEFAULT_SOURCE_NAME,
        version = "内置备用",
        baseUrl = ANIME_DEFAULT_SOURCE_URL,
    )

    companion object {
        private val SUPPORTED_PACKAGES = setOf(ANIME_DEFAULT_SOURCE_PACKAGE)
        private const val KEY_CATALOG = "catalog_json"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/138 Mobile Safari/537.36"
    }
}

internal object XiaoxintvParser {
    fun extractStreamUrl(html: String, gson: Gson): String {
        val json = html.substringAfter("player_aaaa=", missingDelimiterValue = "")
            .substringBefore("</script>")
            .trim()
            .removeSuffix(";")
        if (json.isBlank()) throw IOException("播放页没有返回视频地址")
        val player = gson.fromJson(json, Map::class.java)
        return (player["url"] as? String).orEmpty()
    }
}
