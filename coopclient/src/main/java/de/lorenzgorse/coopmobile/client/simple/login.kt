package de.lorenzgorse.coopmobile.client.simple

import de.lorenzgorse.coopmobile.client.Config
import okhttp3.FormBody
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference

interface CoopLogin {

    enum class Origin {
        Manual, SessionRefresh
    }

    // TODO: this can throw errors that crash the app
    @Throws(IOException::class)
    suspend fun login(
        username: String,
        password: String,
        origin: Origin,
        plan: AtomicReference<String?>? = null
    ): String?

}

class RealCoopLogin(
    private val config: Config,
    private val httpClientFactory: HttpClientFactory,
) : CoopLogin {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Tries to login with the given username and password and returns a session id on success. If
     * the login is not successful, `null` is returned. Reasons for unsuccessful logins include a
     * wrong username or password or a server error.
     */
    override suspend fun login(
        username: String,
        password: String,
        origin: CoopLogin.Origin,
        plan: AtomicReference<String?>?
    ): String? {
        val cookieJar = SessionCookieJar()
        val client = httpClientFactory(cookieJar)

        log.info("Requesting login page")
        val loginFormHtml = client.getHtml(config.loginUrl())

        val authenticityToken = loginFormHtml.safe {
            it.selectFirst("input[name=authenticity_token]")!!.attr("value")
        }
        log.info("Authenticity token is $authenticityToken")

        val reseller = loginFormHtml.safe {
            it.getElementById("user_reseller")!!.attr("value")
        }
        log.info("Reseller is $reseller")

        val formBody = FormBody.Builder()
            .add("authenticity_token", authenticityToken)
            .addEncoded("user[id]", URLEncoder.encode(username, "UTF-8"))
            .addEncoded("user[password]", URLEncoder.encode(password, "UTF-8"))
            .addEncoded("user[reseller]", reseller)
            .add("button", "")
            .build()
        // The server verifies the credentials and redirects to the overview page
        val loginResponse = client.post(config.loginUrl(), formBody)
        // loginResponse.request is the second request, the one resulting from the redirect to the
        // overview page; or if the login failed, resulting from the redirect back to the login
        // page
        val finalUrl = loginResponse.request.url.toUrl().toString()
        log.info("Login result: [${loginResponse.code}] $finalUrl")

        if (!loginResponse.isSuccessful) return null

        val loginSuccessLocation = Regex(config.loginSuccessRegex())
        val match = loginSuccessLocation.find(finalUrl) ?: return null
        plan?.set(match.groups[1]!!.value)
        return cookieJar.get("_ecare_session")?.value
    }

}
