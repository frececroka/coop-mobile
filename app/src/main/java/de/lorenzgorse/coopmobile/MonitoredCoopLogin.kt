package de.lorenzgorse.coopmobile

import android.content.Context
import androidx.core.os.bundleOf
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import de.lorenzgorse.coopmobile.client.simple.CoopException
import de.lorenzgorse.coopmobile.client.simple.CoopLogin
import de.lorenzgorse.coopmobile.components.Fuse
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class MonitoredCoopLogin(
    context: Context,
    private val userProperties: UserProperties,
    private val coopLogin: CoopLogin,
    private val firebaseAnalytics: FirebaseAnalytics,
) : CoopLogin {

    private val successfulLogin = Fuse(context, "Login.Success")

    init {
        // Previously, we used
        //     analytics.logEventOnce(context, "Onb_Login_Success", null)
        // to log the first successful login. We cannot use this anymore, because we'd like to log
        // a "Login" event every time a login happens and use an event parameter to indicate
        // whether the user managed to login successfully before.
        //
        // We now use a fuse to keep track of this condition and have to migrate the state
        // maintained by logEventOnce to the fuse. The logEventOnce method created a file (called
        // "Onb_Login_Success") to remember whether the event has already been logged before. To
        // migrate, we check if the file exists, burn the fuse if it does exist, and remove the
        // file afterwards.
        val logEventOnceFile = File(context.filesDir, "Onb_Login_Success")
        if (logEventOnceFile.exists()) {
            // This event will tell us when it's safe to delete the migration code
            firebaseAnalytics.logEvent("Migration", bundleOf("Feature" to "Onb_Login_Success"))
            successfulLogin.burn()
            logEventOnceFile.delete()
        }
    }

    override suspend fun login(
        username: String,
        password: String,
        origin: CoopLogin.Origin,
        plan: AtomicReference<String?>?
    ): String? {
        val plan = plan ?: AtomicReference()

        val loginEventParameters = bundleOf(
            "Origin" to origin,
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
        } finally {
            firebaseAnalytics.logEvent("Login", loginEventParameters)
        } ?: return null

        successfulLogin.burn()

        plan.get()?.let { userProperties.setPlan(it) }

        return sessionId
    }

}
