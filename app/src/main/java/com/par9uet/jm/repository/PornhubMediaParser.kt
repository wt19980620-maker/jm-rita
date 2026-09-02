package com.par9uet.jm.repository

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.par9uet.jm.data.models.PornhubMediaStream
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class PornhubMediaDefinition(
    val url: String,
    val format: String,
    val quality: Int?,
)

internal object PornhubMediaParser {
    private val mediaDefinitionsPattern = Regex("[\\\"']mediaDefinitions[\\\"']\\s*:\\s*")
    private val directQualityPattern = Regex(
        "[\\\"']quality_(\\d+)p[\\\"']\\s*:\\s*[\\\"']([^\\\"']+)[\\\"']",
        RegexOption.IGNORE_CASE,
    )

    fun parsePage(html: String): List<PornhubMediaDefinition> {
        val definitions = buildList {
            mediaDefinitionsPattern.findAll(html).forEach { match ->
                val arrayStart = html.indexOf('[', startIndex = match.range.last + 1)
                if (arrayStart < 0) return@forEach
                extractBalanced(html, arrayStart, '[', ']')
                    ?.let(::parseJsonDefinitions)
                    ?.let(::addAll)
            }
            directQualityPattern.findAll(html).forEach { match ->
                val quality = match.groupValues[1].toIntOrNull()
                val url = decodeUrl(match.groupValues[2])
                if (url.toHttpUrlOrNull() != null) {
                    add(PornhubMediaDefinition(url, inferFormat(url, "hls"), quality))
                }
            }
        }
        return definitions.distinctBy { it.url }
    }

    fun parseMediaResponse(json: String): List<PornhubMediaDefinition> = runCatching {
        parseJsonDefinitions(JsonParser.parseString(json))
    }.getOrDefault(emptyList())

    fun toStreams(definitions: List<PornhubMediaDefinition>): List<PornhubMediaStream> =
        definitions
            .filter { it.url.toHttpUrlOrNull() != null }
            .map { PornhubMediaStream(it.url, inferFormat(it.url, it.format), it.quality) }
            .distinctBy { it.url }
            .sortedWith(
                compareByDescending<PornhubMediaStream> { it.format.equals("hls", ignoreCase = true) }
                    .thenByDescending { it.quality ?: 0 }
            )

    private fun parseJsonDefinitions(json: String): List<PornhubMediaDefinition> = runCatching {
        parseJsonDefinitions(JsonParser.parseString(json))
    }.getOrDefault(emptyList())

    private fun parseJsonDefinitions(element: JsonElement): List<PornhubMediaDefinition> {
        val elements = when {
            element.isJsonArray -> element.asJsonArray.toList()
            element.isJsonObject -> listOf(element)
            else -> emptyList()
        }
        return elements.mapNotNull { item ->
            if (!item.isJsonObject) return@mapNotNull null
            val value = item.asJsonObject
            val url = value.get("videoUrl")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.let(::decodeUrl)
                .orEmpty()
            if (url.isBlank()) return@mapNotNull null
            val format = value.get("format")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                .orEmpty()
            val quality = value.get("quality")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.filter(Char::isDigit)
                ?.toIntOrNull()
            PornhubMediaDefinition(url, inferFormat(url, format), quality)
        }
    }

    private fun extractBalanced(
        source: String,
        start: Int,
        open: Char,
        close: Char,
    ): String? {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until source.length) {
            val char = source[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return source.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private fun decodeUrl(value: String): String = value
        .replace("\\/", "/")
        .replace("&amp;", "&")
        .replace("\\u0026", "&", ignoreCase = true)

    private fun inferFormat(url: String, declared: String): String = when {
        url.contains(".m3u8", ignoreCase = true) -> "hls"
        declared.isNotBlank() -> declared.lowercase()
        else -> "mp4"
    }
}
