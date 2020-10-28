package de.lorenzgorse.coopmobile.ui.login

import android.app.Activity
import android.os.Bundle
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.CoopModule.coopLogin
import de.lorenzgorse.coopmobile.CoopModule.firebaseCrashlytics
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.coopclient.CoopException.HtmlChanged
import de.lorenzgorse.coopmobile.createAnalytics
import de.lorenzgorse.coopmobile.logEventOnce
import de.lorenzgorse.coopmobile.preferences.writeCredentials
import de.lorenzgorse.coopmobile.preferences.writeSession
import de.lorenzgorse.coopmobile.setScreen
import kotlinx.android.synthetic.main.fragment_login.*
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

val phoneRegex = Pattern.compile("0[0-9\\s]{5,14}").toRegex()
val emailRegex = Pattern.compile(
    "(?:[a-z0-9!#\$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#\$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08" +
            "\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x" +
            "7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\" +
            "[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\\.){3}(?:(2(5[0-5]|[0-4][0-9])" +
            "|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x2" +
            "1-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])"
).toRegex()

class LoginFragment : Fragment() {

    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var analytics: FirebaseAnalytics

    private val loginInProgress = AtomicBoolean(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        analytics = createAnalytics(requireContext())
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        txtPrivacyPolicy.text = HtmlCompat.fromHtml(
            txtPrivacyPolicy.text.toString(), FROM_HTML_MODE_COMPACT)
        txtPrivacyPolicy.movementMethod = LinkMovementMethod.getInstance()

        txtPassword.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_SEND -> { lifecycleScope.launch { attemptLoginGuard() }; true }
                else -> false
            }
        }

        btLogin.setOnClickListener {
            lifecycleScope.launch { attemptLoginGuard() }
        }
    }

    override fun onStart() {
        super.onStart()
        analytics.setScreen("Login")
    }

    private suspend fun attemptLoginGuard() {
        log.info("Starting login.")
        analytics.logEvent("Login_Start", null)
        analytics.logEventOnce(requireContext(), "Onb_Login_Start", null)

        if (!loginInProgress.compareAndSet(false, true)) {
            log.info("Aborting attempt, because another login task is currently running.")
            analytics.logEvent("Login_Abort_Concurrent", null)
            return
        }

        try {
            attemptLogin()
        } finally {
            loginInProgress.compareAndSet(true, false)
        }
    }

    private suspend fun attemptLogin() {
        cardError.visibility = View.GONE
        txtNoNetwork.visibility = View.GONE
        txtLoginFailed.visibility = View.GONE

        val imm = requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(txtUsername.windowToken, 0)
        imm.hideSoftInputFromWindow(txtPassword.windowToken, 0)

        // Reset errors.
        txtUsername.error = null
        txtPassword.error = null

        // Store values at the time of the LoginFragment attempt.
        val username = txtUsername.text.toString()
        val password = txtPassword.text.toString()

        var cancel = false
        var focusView: View? = null

        if (TextUtils.isEmpty(password)) {
            log.info("Cancelling login, because the password is empty.")
            analytics.logEvent("Login_PasswordEmpty", null)
            txtPassword.error = getString(R.string.error_field_required)
            focusView = txtPassword
            cancel = true
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(username)) {
            log.info("Cancelling login, because the username is empty.")
            analytics.logEvent("Login_UsernameEmpty", null)
            txtUsername.error = getString(R.string.error_field_required)
            focusView = txtUsername
            cancel = true
        } else if (!isUsernameValid(username)) {
            log.info("Cancelling login, because the username is invalid.")
            analytics.logEvent("Login_UsernameInvalid", null)
            txtUsername.error = getString(R.string.error_invalid_username)
            focusView = txtUsername
            cancel = true
        }

        if (cancel) {
            analytics.logEvent("Login_InputError", null)
            focusView?.requestFocus()
            return
        }

        showProgress(true)

        log.info("Performing login.")
        analytics.logEvent("Login_SendRequest", null)

        val sessionId = try {
            log.info("Trying to obtain session ID using provided username and password.")
            coopLogin.login(username, password)
        } catch (e: IOException) {
            log.error("No network connection available.")
            analytics.logEvent("Login_NoNetwork", null)
            showProgress(false)
            cardError.visibility = View.VISIBLE
            txtNoNetwork.visibility = View.VISIBLE
            return
        } catch (e: HtmlChanged) {
            log.error("HTML structure changed unexpectedly.", e)
            analytics.logEvent("Login_HtmlChanged", null)
            firebaseCrashlytics().recordException(e)
            showProgress(false)
            Toast.makeText(context, R.string.update_necessary, Toast.LENGTH_LONG).show()
            return
        }

        return if (sessionId != null) {
            log.info("Obtained session ID.")
            analytics.logEvent("Login_Success", null)
            analytics.logEventOnce(requireContext(), "Onb_Login_Success", null)
            writeCredentials(requireContext(), username, password)
            writeSession(requireContext(), sessionId)
            findNavController().navigate(R.id.action_overview)
        } else {
            log.info("Did not receive any session ID.")
            analytics.logEvent("Login_AuthFailed", null)
            showProgress(false)
            cardError.visibility = View.VISIBLE
            txtLoginFailed.visibility = View.VISIBLE
        }
    }

    private fun isUsernameValid(username: String): Boolean {
        return username.matches(phoneRegex) || username.matches(emailRegex)
    }

    private fun showProgress(show: Boolean) {
        login_form?.visibility = if (show) View.GONE else View.VISIBLE
        loading?.visibility = if (show) View.VISIBLE else View.GONE
    }

}
