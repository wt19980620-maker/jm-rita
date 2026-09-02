package com.par9uet.jm.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PornhubMediaParserTest {
    @Test
    fun parsesBalancedMediaDefinitionsWithBracketsInsideUrl() {
        val html = """
            <script>
            var flashvars_123 = {
              "mediaDefinitions": [
                {"format":"hls","quality":"720","videoUrl":"https:\/\/cdn.example\/a[1].m3u8?h=abc\u0026e=2"},
                {"format":"mp4","quality":[],"videoUrl":"https://example.com/video/get_media?id=1&amp;x=2"}
              ],
              "next": true
            };
            </script>
        """.trimIndent()

        val definitions = PornhubMediaParser.parsePage(html)

        assertEquals(2, definitions.size)
        assertEquals("https://cdn.example/a[1].m3u8?h=abc&e=2", definitions[0].url)
        assertEquals(720, definitions[0].quality)
        assertEquals(null, definitions[1].quality)
    }

    @Test
    fun parsesResolvedMediaResponseAndPrefersHls() {
        val definitions = PornhubMediaParser.parseMediaResponse(
            """[
              {"format":"mp4","quality":"1080","videoUrl":"https://cdn.example/video.mp4"},
              {"format":"hls","quality":"480","videoUrl":"https://cdn.example/video.m3u8"}
            ]"""
        )

        val streams = PornhubMediaParser.toStreams(definitions)

        assertEquals("hls", streams.first().format)
        assertEquals("480p", streams.first().label)
    }

    @Test
    fun parsesDirectQualityFallbackAndRejectsInvalidUrl() {
        val html = """
            "quality_1080p":"https:\/\/cdn.example\/1080.m3u8",
            "quality_720p":"javascript:alert(1)"
        """.trimIndent()

        val definitions = PornhubMediaParser.parsePage(html)

        assertEquals(1, definitions.size)
        assertEquals(1080, definitions.single().quality)
        assertTrue(definitions.single().url.startsWith("https://"))
    }
}
