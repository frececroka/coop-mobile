package de.lorenzgorse.coopmobile.client.simple

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.jsoup.Jsoup
import org.jsoup.UncheckedIOException
import org.jsoup.nodes.Document
import java.net.URL

typealias HttpClientFactory = (CookieJar) -> HttpClient

interface HttpClient {
    suspend fun <T> getJson(url: String, type: Class<T>, check: (Response) -> Unit): T
    suspend fun getHtml(url: String, check: (Response) -> Unit = {}): Document
    suspend fun getText(url: String, check: (Response) -> Unit = {}): String
    suspend fun post(url: String, body: FormBody): Response
    suspend fun post(url: URL, body: FormBody): Response
}

class SimpleHttpClient(cookieJar: CookieJar = SessionCookieJar()) : HttpClient {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .cookieJar(cookieJar)
        .build()

    override suspend fun <T> getJson(url: String, type: Class<T>, check: (Response) -> Unit): T =
        Gson().fromJson(getText(url, check), type)

    override suspend fun getHtml(url: String, check: (Response) -> Unit): Document {
        val html = getText(url, check)
        return try {
            Jsoup.parse(html)
        } catch (e: UncheckedIOException) {
            throw CoopException.BadHtml(e, html)
        }
    }

    override suspend fun getText(url: String, check: (Response) -> Unit): String =
        withContext(Dispatchers.IO) { get(url, check).body!!.string() }

    private suspend fun get(url: String, check: (Response) -> Unit): Response {
        val request = Request.Builder().get().url(url).build()
        return call(request, check)
    }

    override suspend fun post(url: String, body: FormBody) =
        post(URL(url), body)

    override suspend fun post(url: URL, body: FormBody): Response {
        val request = Request.Builder().post(body).url(url).build()
        return call(request)
    }

    private suspend fun call(request: Request, check: (Response) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().also(check)
        }

}
