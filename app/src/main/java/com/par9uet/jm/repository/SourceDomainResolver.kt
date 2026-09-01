package com.par9uet.jm.repository

import com.par9uet.jm.config.SOURCE_RELEASE_PAGE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit

class SourceDomainResolver {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun resolve(): String? = withContext(Dispatchers.IO) {
        val releaseUrl = SOURCE_RELEASE_PAGE.toHttpUrlOrNull() ?: return@withContext null
        execute(Request.Builder().url(releaseUrl).head().build()).use { headResponse ->
            val redirectedWebsite = SourceDomainParser.normalizeWebsite(
                headResponse.request.url.toString()
            )
            if (
                redirectedWebsite != null &&
                headResponse.request.url.host != releaseUrl.host &&
                SourceDomainParser.isLikelySourceWebsite(redirectedWebsite)
            ) {
                return@withContext redirectedWebsite
            }
        }

        execute(Request.Builder().url(releaseUrl).get().build()).use { response ->
            val redirectedWebsite = SourceDomainParser.normalizeWebsite(
                response.request.url.toString()
            )
            if (
                redirectedWebsite != null &&
                response.request.url.host != releaseUrl.host &&
                SourceDomainParser.isLikelySourceWebsite(redirectedWebsite)
            ) {
                return@withContext redirectedWebsite
            }
            if (!response.isSuccessful) {
                return@withContext null
            }
            SourceDomainParser.extractWebsite(
                html = response.body.string(),
                releasePage = SOURCE_RELEASE_PAGE,
            )
        }
    }

    private fun execute(request: Request): Response = client.newCall(request).execute()
}

object SourceDomainParser {
    private val baseUrlPattern = Regex(
        """base_url\s*=\s*[\"'](https://[^\"']+)[\"']""",
        RegexOption.IGNORE_CASE,
    )
    private val httpsUrlPattern = Regex("""https://[A-Za-z0-9.-]+(?:/[^\"'<>\s]*)?""")
    private val domainSectionPattern = Regex(
        """<div\s+class=[\"']([^\"']+)[\"'][^>]*>(.*?)</div>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val spanDomainPattern = Regex(
        """<span[^>]*>\s*([A-Za-z0-9.-]+\.[A-Za-z]{2,})\s*</span>""",
        RegexOption.IGNORE_CASE,
    )
    private val sectionPriority = listOf(
        "china",
        "first_line",
        "second_line",
        "international",
        "southeast_asia",
    )

    fun extractWebsite(html: String, releasePage: String): String? {
        val releaseHost = releasePage.toHttpUrlOrNull()?.host
        val candidates = buildList {
            baseUrlPattern.find(html)?.groupValues?.getOrNull(1)?.let(::add)
            domainSectionPattern.findAll(html)
                .map { match -> match.groupValues[1] to match.groupValues[2] }
                .sortedBy { (className, _) ->
                    sectionPriority.indexOf(className).takeIf { it >= 0 } ?: Int.MAX_VALUE
                }
                .forEach { (_, sectionHtml) ->
                    spanDomainPattern.findAll(sectionHtml).forEach { match ->
                        add(match.groupValues[1])
                    }
                }
            httpsUrlPattern.findAll(html).forEach { add(it.value) }
        }
        return candidates.asSequence()
            .mapNotNull(::normalizeWebsite)
            .firstOrNull {
                val host = it.toHttpUrlOrNull()?.host
                host != releaseHost && host != null && isLikelySourceHost(host)
            }
    }

    fun normalizeWebsite(value: String): String? {
        val trimmedValue = value.trim()
        if (trimmedValue.startsWith("http://", ignoreCase = true)) {
            return null
        }
        val url = (if (trimmedValue.contains("://")) trimmedValue else "https://$trimmedValue")
            .toHttpUrlOrNull() ?: return null
        if (!url.isHttps || !url.host.contains('.') || url.host.all { it.isDigit() || it == '.' }) {
            return null
        }
        return url.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()
            .removeSuffix("/")
    }

    fun isLikelySourceWebsite(value: String): Boolean {
        val host = value.toHttpUrlOrNull()?.host ?: return false
        return isLikelySourceHost(host)
    }

    private fun isLikelySourceHost(host: String): Boolean {
        val normalizedHost = host.lowercase()
        return listOf("18comic", "jmcomic", "comic18j", "jc-").any(normalizedHost::contains)
    }
}
