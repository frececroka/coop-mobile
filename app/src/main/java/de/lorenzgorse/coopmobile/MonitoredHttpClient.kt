package de.lorenzgorse.coopmobile

import android.content.Context
import com.google.gson.Gson
import de.lorenzgorse.coopmobile.client.simple.HttpClient
import de.lorenzgorse.coopmobile.ui.overview.RealFirstInstallTimeProvider
import okhttp3.FormBody
import okhttp3.Response
import org.jsoup.nodes.Document
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ISO_DATE
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import java.util.*

class MonitoredHttpClient(
    context: Context,
    private val httpClient: HttpClient
) : HttpClient {

    data class Record(
        val url: String,
        val dateTime: String,
        val shape: HtmlShape?,
    )

    private val fileUploader = FileUploader(context)
    private val userProperties = UserProperties(context)

    // TODO: this is ugly
    private val firstInstallTime = Instant.ofEpochMilli(RealFirstInstallTimeProvider().get(context))

    override suspend fun <T> getJson(url: String, type: Class<T>, check: (Response) -> Unit): T {
        return httpClient.getJson(url, type, check)
    }

    override suspend fun getHtml(url: String, check: (Response) -> Unit): Document {
        val html = httpClient.getHtml(url, check)
        val now = Instant.now()
        val date = formatInstant(ISO_DATE, now)
        val dateTime = formatInstant(ISO_DATE_TIME, now)
        val record = Record(url, dateTime, html.body().shape())
        val recordBytes = gzip(Gson().toJson(record).toByteArray())
        val userPseudoId = userPseudoId() ?: "unknown-user"
        val uuid = UUID.randomUUID()
        val path = "htmlshape/$date/$userPseudoId/$uuid.gz"
        val metadata = buildMap {
            put("url", url)
            put("firstInstallTime", formatInstant(ISO_DATE_TIME, firstInstallTime))
            val plan = userProperties.data().plan
            if (plan != null) {
                put("plan", plan)
            }
        }
        fileUploader.upload(path, metadata, recordBytes)
        return html
    }

    private fun formatInstant(formatter: DateTimeFormatter, instant: Instant): String =
        formatter.format(instant.atZone(ZoneId.of("UTC")))

    override suspend fun getText(url: String, check: (Response) -> Unit): String {
        return httpClient.getText(url, check)
    }

    override suspend fun post(url: String, body: FormBody): Response {
        return httpClient.post(url, body)
    }

    override suspend fun post(url: URL, body: FormBody): Response {
        return httpClient.post(url, body)
    }

}
