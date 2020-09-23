package de.lorenzgorse.coopmobile.coopclient

import okhttp3.FormBody
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URLEncoder

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

        val authenticityToken = loginFormHtml.safe {
            selectFirst("input[name=authenticity_token]").attr("value")
        }
        log.info("Authenticity token is $authenticityToken")

        val reseller = loginFormHtml.safe {
            getElementById("user_reseller").attr("value")
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
        val finalUrl = loginResponse.request.url.toUrl().toString()
        log.info("Login result: [${loginResponse.code}] $finalUrl")

        if (!loginResponse.isSuccessful) return null

        // https://myaccount.coopmobile.ch/eCare/wireless/de
        // https://myaccount.coopmobile.ch/eCare/prepaid/de
        // https://myaccount.coopmobile.ch/eCare/wireless/de?login=true
        // https://myaccount.coopmobile.ch/eCare/prepaid/de?login=true
        val loginSuccessLocation = Regex("${Regex.escape(coopBase)}/.*/(de|fr|it)/?(\\?login=true)?")
        return if (finalUrl.matches(loginSuccessLocation)) {
            cookieJar.get("_ecare_session")?.value
        } else {
            null
        }
    }

}
