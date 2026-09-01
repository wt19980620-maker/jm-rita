package com.par9uet.jm.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceDomainResolverTest {
    @Test
    fun extractsBaseUrlBeforeOtherPageLinks() {
        val html = """
            <script>var base_url = "https://comic18j-new.example/path";</script>
            <a href="https://ads.example/banner">ad</a>
        """.trimIndent()

        assertEquals(
            "https://comic18j-new.example",
            SourceDomainParser.extractWebsite(html, "https://release.example/link"),
        )
    }

    @Test
    fun ignoresReleasePageAndInvalidHosts() {
        val html = """
            https://release.example/another
            https://127.0.0.1/private
            https://comic18j-current.example/home
        """.trimIndent()

        assertEquals(
            "https://comic18j-current.example",
            SourceDomainParser.extractWebsite(html, "https://release.example/link"),
        )
        assertNull(SourceDomainParser.normalizeWebsite("http://current.example"))
    }

    @Test
    fun prioritizesChinaDomainFromPublishingPage() {
        val html = """
            <div class="international"><span>18comic.vip</span></div>
            <div class="southeast_asia"><span>jmcomic-zzz.one</span></div>
            <div class="china"><span>comic18j-rita.net</span></div>
            <div class="first_line"><span>comic18j-rita.club</span></div>
        """.trimIndent()

        assertEquals(
            "https://comic18j-rita.net",
            SourceDomainParser.extractWebsite(html, "https://jmcomicui.net/"),
        )
    }
}
