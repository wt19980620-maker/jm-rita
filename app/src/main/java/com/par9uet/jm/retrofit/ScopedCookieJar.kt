package com.par9uet.jm.retrofit

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class ScopedCookieJar(private val persist: (List<Cookie>) -> Unit) : CookieJar {
    private var cookies = emptyList<Cookie>()

    @Synchronized
    fun restore(saved: List<Cookie>) {
        cookies = saved.filter { it.expiresAt > System.currentTimeMillis() }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        this.cookies = (this.cookies + cookies.filter { it.matches(url) })
            .associateBy { Triple(it.domain, it.path, it.name) }
            .values.filter { it.expiresAt > System.currentTimeMillis() }
        persist(this.cookies)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.filter {
        it.expiresAt > System.currentTimeMillis() && it.matches(url)
    }
}
