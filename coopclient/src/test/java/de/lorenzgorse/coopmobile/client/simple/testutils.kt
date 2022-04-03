package de.lorenzgorse.coopmobile.client.simple

import okhttp3.CookieJar

fun httpClientFactory(cookieJar: CookieJar): HttpClient =
    SimpleHttpClient(cookieJar)
