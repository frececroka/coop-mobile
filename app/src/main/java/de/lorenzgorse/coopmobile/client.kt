package de.lorenzgorse.coopmobile

import com.google.gson.Gson
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL

fun OkHttpClient.get(url: URL): Response {
    val statusRequest = Request.Builder().get().url(url).build()
    return newCall(statusRequest).execute()
}

inline fun <reified T> OkHttpClient.getJson(url: URL, check: (Response) -> Unit): T = Gson().fromJson(getText(url, check), T::class.java)

inline fun OkHttpClient.getHtml(url: URL, check: (Response) -> Unit): Document = Jsoup.parse(getText(url, check))

inline fun OkHttpClient.getText(url: URL, check: (Response) -> Unit): String {
    val response = get(url).also(check)
    return response.body!!.string()
}

fun OkHttpClient.post(url: URL, body: FormBody): Response {
    val statusRequest = Request.Builder().post(body).url(url).build()
    return newCall(statusRequest).execute()
}
