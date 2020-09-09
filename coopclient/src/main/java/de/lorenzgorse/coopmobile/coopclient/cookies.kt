package de.lorenzgorse.coopmobile.coopclient

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class StaticCookieJar(sessionId: String) : CookieJar {

    private val sessionCookie: Cookie = Cookie.Builder()
        .domain("myaccount.coopmobile.ch")
        .name("_ecare_session")
        .value(sessionId)
        .build()

    private val cookies: MutableMap<String, Cookie> = mutableMapOf(
        Pair(sessionCookie.name, sessionCookie)
    )

    override fun loadForRequest(url: HttpUrl): MutableList<Cookie> {
        return cookies.values.toMutableList()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach {
            this.cookies[it.name] = it
        }
    }

}

class SessionCookieJar : CookieJar {

    private val cookies = HashMap<String, Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            this.cookies[cookie.name] = cookie
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookies.values.toList()
    }

    fun get(name: String): Cookie? {
        return cookies[name]
    }

}
