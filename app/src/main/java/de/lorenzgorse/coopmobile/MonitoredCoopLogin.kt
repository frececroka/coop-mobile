package de.lorenzgorse.coopmobile

import android.content.Context
import androidx.core.os.bundleOf
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import de.lorenzgorse.coopmobile.client.simple.CoopException
import de.lorenzgorse.coopmobile.client.simple.CoopLogin
import de.lorenzgorse.coopmobile.components.Fuse
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class MonitoredCoopLogin(
    context: Context,
    private val coopLogin: CoopLogin,
    private val userProperties: UserProperties,
    private val firebaseAnalytics: FirebaseAnalytics,
) : CoopLogin {

    private val successfulLogin = Fuse(context, "Login.Success")

    override suspend fun login(
        username: String,
        password: String,
        origin: CoopLogin.Origin,
        plan: AtomicReference<String?>?
    ): String? {
        val plan = plan ?: AtomicReference()

        val loginEventParameters = bundleOf(
            "Origin" to origin.name,
            "NewUser" to !successfulLogin.isBurnt(),
        )

        val sessionId = try {
            coopLogin.login(username, password, origin, plan).also {
                loginEventParameters.putString(
                    "Status",
                    if (it != null) "Success" else "AuthFailed"
                )
            }
        } catch (e: IOException) {
            loginEventParameters.putString("Status", "NoNetwork")
            throw e
        } catch (e: CoopException.HtmlChanged) {
            loginEventParameters.putString("Status", "HtmlChanged")
            Firebase.crashlytics.recordException(e)
            throw e
        } catch (e: Exception) {
            loginEventParameters.putString("Exception", e.javaClass.simpleName)
            throw e
        } finally {
            firebaseAnalytics.logEvent("Login", loginEventParameters)
        } ?: return null

        successfulLogin.burn()

        plan.get()?.let { userProperties.setPlan(it) }

        return sessionId
    }

}
