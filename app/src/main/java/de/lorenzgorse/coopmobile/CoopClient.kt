package de.lorenzgorse.coopmobile

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.annotations.SerializedName
import de.lorenzgorse.coopmobile.CoopClient.CoopException.*
import de.lorenzgorse.coopmobile.CoopModule.coopLogin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.Response
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.Serializable
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

data class UnitValue<T>(val description: String, val amount: T, val unit: String)

data class CoopData(val credit: UnitValue<Float>?, val consumptions: List<UnitValue<Int>>)

data class Product(
    val name: String,
    val description: String,
    val price: String,
    val buySpec: ProductBuySpec
)

data class ProductBuySpec(val url: String, val parameters: Map<String, String>) : Serializable

data class CorrespondenceHeader(val date: Date, val subject: String, val details: URL)
data class Correspondence(val header: CorrespondenceHeader, val message: String)

data class RawConsumptionLogEntry(
    @SerializedName("start_date") val date: String,
    @SerializedName("is_data") val isData: Boolean,
    val type: String,
    val amount: String
)

data class RawConsumptionLog(val data: List<RawConsumptionLogEntry>)

interface ConsumptionLogEntry
data class DataConsumptionLogEntry(
    val date: Date,
    val type: String,
    val amount: Double
) : ConsumptionLogEntry

const val coopHost = "myaccount.coopmobile.ch"
const val coopScheme = "https"
const val coopBase = "$coopScheme://$coopHost/eCare"
val country = determineCountry()
val coopBaseLogin = "$coopBase/$country/users/sign_in"
val coopBaseAccount = "$coopBase/prepaid/$country"

fun determineCountry(): String {
    return when (Locale.getDefault().language) {
        "de" -> "de"
        "it" -> "it"
        "fr" -> "fr"
        else -> "de"
    }
}

interface CoopClient {

    sealed class CoopException(cause: Throwable?) : Exception(cause) {
        class UnauthorizedException(val redirect: String?) : CoopException(null)
        class HtmlChangedException(cause: Throwable) : CoopException(cause)
        class PlanUnsupported(val plan: String?) : CoopException(null)
    }

    @Throws(IOException::class, CoopException::class)
    suspend fun getData(): CoopData

    @Throws(IOException::class, CoopException::class)
    suspend fun getConsumptionLog(): List<ConsumptionLogEntry>

    @Throws(IOException::class, CoopException::class)
    suspend fun getProducts(): List<Product>

    @Throws(IOException::class, CoopException::class)
    suspend fun buyProduct(buySpec: ProductBuySpec): Boolean

    @Throws(IOException::class, CoopException::class)
    suspend fun getCorrespondeces(): List<CorrespondenceHeader>

    @Throws(IOException::class, CoopException::class)
    suspend fun augmentCorrespondence(header: CorrespondenceHeader): Correspondence

    fun sessionId(): String

}

class RealCoopClient(private val sessionId: String) : CoopClient {

    private val log = LoggerFactory.getLogger(javaClass)

    private var client = HttpClient(StaticCookieJar(sessionId))

    override suspend fun getData(): CoopData {
        val html = getHtml(coopBaseAccount)
        return safeHtml {
            val creditBalance = html.selectFirst("#credit_balance")?.let { block ->
                parseUnitValueBlock(block.parent(), { it.toFloat() }) { it.replace(".–", "") }
            }
            val consumptions = html.select("#my_consumption .panel").map {
                parseUnitValueBlock(it, { v -> v.toInt() })
            }
            CoopData(creditBalance, consumptions)
        }
    }

    private fun <T> parseUnitValueBlock(
        block: Element,
        convert: (String) -> T,
        sanitize: (String) -> String = { it }
    ): UnitValue<T> {
        val title = block.selectFirst(".panel__title").text()
        val amount = block.selectFirst(".panel__consumption__data--value").text()
        val unit = block.selectFirst(".panel__consumption__data--unit").text()
        return UnitValue(title, convert(sanitize(amount)), unit)
    }

    override suspend fun getConsumptionLog(): List<ConsumptionLogEntry> {
        val consumption = getJson<RawConsumptionLog>(
            "https://myaccount.coopmobile.ch/ajax_load_cdr")
        return consumption.data.mapNotNull { parseConsumptionLogEntry(it) }
    }

    private fun parseConsumptionLogEntry(
        rawConsumptionLogEntry: RawConsumptionLogEntry
    ): ConsumptionLogEntry? {
        if (!rawConsumptionLogEntry.isData) {
            return null
        }

        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMAN)
        val date = dateFormat.parse(rawConsumptionLogEntry.date)!!
        val type = rawConsumptionLogEntry.type
        val amount = rawConsumptionLogEntry.amount.replace("&#39;", "").toDouble()
        return DataConsumptionLogEntry(date, type, amount)
    }

    override suspend fun getProducts(): List<Product> {
        val html = getHtml("$coopBaseAccount/add_product")
        return safeHtml { html.select(".add_product").map { parseProductBlock(it) } }
    }

    private fun parseProductBlock(productBlock: Element): Product {
        val content = productBlock.selectFirst(".modal-body")

        var name = ""
        var price = ""
        val descriptions = arrayListOf<String>()

        for (child in content.childNodes()) {
            when (child) {
                is TextNode -> descriptions.add(child.text())
                is Element ->
                    when {
                        child.hasClass("modal__content__info") ->
                            name = child.text()
                        child.hasClass("modal__content__price") ->
                            price = child.text()
                        else ->
                            descriptions.add(child.text())
                    }
            }
        }

        val description = descriptions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n") {
                if (!it.endsWith(".") && !it.endsWith(":")) {
                    "$it."
                } else {
                    it
                }
            }

        val form = productBlock.selectFirst("form")
        val url = form.attr("action")
        val parameters = form.select("input")
            .map { Pair(it.attr("name"), it.attr("value")) }
            .toMap()
        val buySpec = ProductBuySpec(url, parameters)

        return Product(name, description, price, buySpec)
    }

    override suspend fun buyProduct(buySpec: ProductBuySpec): Boolean {
        log.info("Buying according to $buySpec")
        val body = buySpec.parameters.let {
            val builder = FormBody.Builder()
            for ((name, value) in it) {
                builder.add(name, value)
            }
            builder.build()
        }
        val response = client.post(URL(coopScheme, coopHost, buySpec.url), body)
        log.info("Buy request completed with status code: ${response.code}")
        return response.isRedirect || response.isSuccessful
    }

    override suspend fun getCorrespondeces(): List<CorrespondenceHeader> {
        val html = getHtml("$coopBaseAccount/my_correspondence/index?limit=30")
        return safeHtml { html.select(".table--mail tbody tr").map { parseCorrespondenceRow(it) } }
    }

    private fun parseCorrespondenceRow(it: Element): CorrespondenceHeader {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
        val date = dateFormat.parse(it.selectFirst(".first").text())!!
        val subject = it.selectFirst(".second").text()
        val details = URL(coopScheme, coopHost, it.selectFirst("a").attr("href"))
        return CorrespondenceHeader(date, subject, details)
    }

    override suspend fun augmentCorrespondence(header: CorrespondenceHeader): Correspondence {
        return Correspondence(header, getCorrespondenceMessage(header.details))
    }

    private suspend fun getCorrespondenceMessage(url: URL): String {
        val html = getHtml(url.toString())
        return safeHtml { html.selectFirst(".panel__print__content").text() }
    }

    override fun sessionId() = sessionId

    private suspend fun getHtml(url: String) = client.getHtml(url, ::assertResponseSuccessful)
    private suspend inline fun <reified T> getJson(url: String): T =
        client.getJson(url, ::assertResponseSuccessful)

    companion object {

        private val signInRegex =
            Regex("https://myaccount\\.coopmobile\\.ch/eCare/([^/]+)/users/sign_in")
        private val planRegex =
            Regex("https://myaccount\\.coopmobile\\.ch/eCare/([^/]+)/.+")

        fun assertResponseSuccessful(response: Response) {
            if (!response.isRedirect) return
            val location = response.header("Location") ?:
                throw UnauthorizedException(null)
            throw if (signInRegex.matches(location)) {
                UnauthorizedException(location)
            } else {
                val matchResult = planRegex.matchEntire(location)
                if (matchResult != null) {
                    val plan = matchResult.groups[1]?.value
                    if (plan != "prepaid") {
                        PlanUnsupported(plan)
                    } else {
                        // This accounts for ... interesting behavior on the part of Coop Mobile.
                        // Requesting the overview page with an expired session redirects to the
                        // same page again (probably setting a cookie).
                        UnauthorizedException(location)
                    }
                } else {
                    UnauthorizedException(location)
                }
            }
        }

    }

}


interface CoopLogin {
    @Throws(IOException::class)
    suspend fun login(username: String, password: String): String?
}

class RealCoopLogin : CoopLogin {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Tries to login with the given username and password and returns a session id on success. If
     * the login is not successful, `null` is returned. Reasons for unsuccessful logins include a
     * wrong username or password or a server error.
     */
    override suspend fun login(username: String, password: String): String? {
        val cookieJar = SessionCookieJar()
        val client = HttpClient(cookieJar)

        log.info("Requesting $coopBaseLogin")
        val loginFormHtml = client.getHtml(coopBaseLogin)

        val authenticityToken = safeHtml {
            loginFormHtml
                .selectFirst("input[name=authenticity_token]")
                .attr("value")
        }
        log.info("Authenticity token is $authenticityToken")

        val reseller = safeHtml {
            loginFormHtml
                .getElementById("user_reseller")
                .attr("value")
        }
        log.info("Reseller is $reseller")

        val formBody = FormBody.Builder()
            .add("authenticity_token", authenticityToken)
            .addEncoded("user[id]", URLEncoder.encode(username, "UTF-8"))
            .addEncoded("user[password]", URLEncoder.encode(password, "UTF-8"))
            .addEncoded("user[reseller]", reseller)
            .add("button", "")
            .build()
        val loginResponse = client.post(coopBaseLogin, formBody)
        log.info("Login response status code is ${loginResponse.code}")

        val location = loginResponse.headers["Location"]
        log.info("Login redirect URL is $location")

        val loginSuccessLocation = Regex("${Regex.escape(coopBase)}/[a-z]+/home")
        return if (
            loginResponse.isRedirect &&
            location?.matches(loginSuccessLocation) == true
        ) {
            cookieJar.get("_ecare_session")?.value
        } else {
            null
        }
    }

}


interface CoopClientFactory {
    suspend fun get(context: Context): CoopClient?
    suspend fun refresh(context: Context, invalidateSession: Boolean = false): CoopClient?
}

class RealCoopClientFactory : CoopClientFactory {

    private var instance: CoopClient? = null

    private val refreshMtx = Mutex()
    override suspend fun get(context: Context): CoopClient? = refreshMtx.withLock {
        if (instance == null) {
            refresh(context)
        }
        return instance
    }

    override suspend fun refresh(context: Context, invalidateSession: Boolean): CoopClient? {
        val sessionId = newSession(context, invalidateSession) ?: return null
        return RealCoopClient(sessionId).also { instance = it }
    }

    private val sessionMtx = Mutex()
    private suspend fun newSession(context: Context, invalidateSession: Boolean): String? = sessionMtx.withLock {
        val sessionId = if (invalidateSession) {
            newSessionFromSavedCredentials(context)
        } else {
            loadSavedSession(context) ?: newSessionFromSavedCredentials(context)
        }
        if (sessionId != null) {
            writeSession(context, sessionId)
        }
        sessionId
    }

}

fun loadSavedSession(context: Context): String? {
    return context.getCoopSharedPreferences().getString("session", null)
}

fun writeSession(context: Context, sessionId: String) {
    context.getCoopSharedPreferences().edit().putString("session", sessionId).apply()
}

fun clearSession(context: Context) {
    context.getCoopSharedPreferences().edit().remove("session").apply()
}

suspend fun newSessionFromSavedCredentials(context: Context): String? {
    val (username, password) = loadSavedCredentials(context) ?: return null
    return coopLogin.login(username, password)
}

fun loadSavedCredentials(context: Context): Pair<String, String>? {
    val username = context.getCoopSharedPreferences().getString("username", null)
    val password = context.getCoopSharedPreferences().getString("password", null)
    return if (username != null && password != null) {
        Pair(username, password)
    } else loadSavedCredentialsFromFile(context)
}

fun loadSavedCredentialsFromFile(context: Context): Pair<String, String>? {
    val loginLines = try {
        context.filesDir.resolve(File("login.txt")).readLines()
    } catch (e: FileNotFoundException) {
        return null
    }

    val username = loginLines[0].trim()
    val password = loginLines[1].trim()
    writeCredentials(context, username, password)

    return Pair(username, password)
}

fun writeCredentials(context: Context, username: String, password: String) {
    context.getCoopSharedPreferences().edit()
        .putString("username", username)
        .putString("password", password)
        .apply()
}

fun clearCredentials(context: Context) {
    context.getCoopSharedPreferences().edit()
        .remove("username")
        .remove("password")
        .apply()
}

fun Context.getCoopSharedPreferences(): SharedPreferences {
    return this.getSharedPreferences("de.lorenzgorse.coopmobile", Context.MODE_PRIVATE)
}

/**
 * Executes the given closure and translates all [NullPointerException]s and
 * [IllegalStateException]s to [HtmlChangedException]s. This is used for code that extracts data
 * from the DOM.
 */
fun <T> safeHtml(fn: () -> T): T {
    return try {
        fn()
    } catch (e: NullPointerException) {
        throw HtmlChangedException(e)
    } catch (e: IllegalStateException) {
        throw HtmlChangedException(e)
    }
}
