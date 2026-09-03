package com.par9uet.jm.repository

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class XiaoxintvParserTest {
    @Test
    fun extractsEscapedHlsUrl() {
        val html = """
            <script>var player_aaaa={"url":"https:\/\/cdn.example.com\/video\/index.m3u8"};</script>
        """.trimIndent()

        assertEquals(
            "https://cdn.example.com/video/index.m3u8",
            XiaoxintvParser.extractStreamUrl(html, Gson()),
        )
    }

    @Test
    fun rejectsPageWithoutPlayerData() {
        assertThrows(IOException::class.java) {
            XiaoxintvParser.extractStreamUrl("<html></html>", Gson())
        }
    }
}
