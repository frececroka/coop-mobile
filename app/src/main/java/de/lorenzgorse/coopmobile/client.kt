package de.lorenzgorse.coopmobile

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL

class HttpClient(cookieJar: CookieJar = SessionCookieJar()) {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .cookieJar(cookieJar)
        .build()

    suspend inline fun <reified T> getJson(url: String, noinline check: (Response) -> Unit): T =
        Gson().fromJson(getText(url, check), T::class.java)

    suspend fun getHtml(url: String, check: (Response) -> Unit = {}): Document =
        Jsoup.parse(getText(url, check))

    suspend fun getText(url: String, check: (Response) -> Unit = {}): String =
        withContext(Dispatchers.IO) { get(url, check).body!!.string() }

    private suspend fun get(url: String, check: (Response) -> Unit): Response {
        val request = Request.Builder().get().url(url).build()
        return call(request, check)
    }

    suspend fun post(url: String, body: FormBody) =
        post(URL(url), body)

    suspend fun post(url: URL, body: FormBody): Response {
        val request = Request.Builder().post(body).url(url).build()
        return call(request)
    }

    private suspend fun call(request: Request, check: (Response) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().also(check)
        }

}
