package com.par9uet.jm.retrofit

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopedCookieJarTest {
    private val url = "https://api.example.test/private/list".toHttpUrl()
    private fun cookie() = Cookie.Builder().name("session").value("fixture")
        .hostOnlyDomain(url.host).path("/private").secure().build()

    @Test
    fun onlySendsCookiesToMatchingSecureOriginAndPath() {
        val jar = ScopedCookieJar {}
        jar.saveFromResponse(url, listOf(cookie()))
        assertEquals(1, jar.loadForRequest(url).size)
        assertTrue(jar.loadForRequest("https://other.example.test/private/list".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://sub.api.example.test/private/list".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://api.example.test/public".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("http://api.example.test/private/list".toHttpUrl()).isEmpty())
    }

    @Test
    fun expiredResponseDeletesPersistedCookieAndRejectsForeignCookies() {
        var saved = emptyList<Cookie>()
        val jar = ScopedCookieJar { saved = it }
        jar.saveFromResponse(url, listOf(cookie()))
        jar.saveFromResponse(url, listOf(cookie().newBuilderForTest()))
        assertTrue(saved.isEmpty())
        jar.saveFromResponse(url, listOf(Cookie.Builder().name("session").value("fixture")
            .domain("other.example.test").build()))
        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    @Test
    fun restoredCookiesRespectExpiryAndCanBeCleared() {
        val jar = ScopedCookieJar {}
        jar.restore(listOf(cookie(), cookie().newBuilderForTest()))
        assertEquals(1, jar.loadForRequest(url).size)
        jar.restore(emptyList())
        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    private fun Cookie.newBuilderForTest(): Cookie = Cookie.Builder().name(name).value("")
        .hostOnlyDomain(domain).path(path).secure().expiresAt(1L).build()
}
