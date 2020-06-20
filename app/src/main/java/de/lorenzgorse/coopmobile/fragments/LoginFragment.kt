package de.lorenzgorse.coopmobile.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.os.AsyncTask
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
import androidx.navigation.fragment.findNavController
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.*
import de.lorenzgorse.coopmobile.CoopClient.CoopException.HtmlChangedException
import de.lorenzgorse.coopmobile.CoopModule.coopLogin
import de.lorenzgorse.coopmobile.CoopModule.firebaseCrashlytics
import de.lorenzgorse.coopmobile.fragments.LoginFragment.LoginStatus.*
import kotlinx.android.synthetic.main.fragment_login.*
import org.slf4j.LoggerFactory
import java.io.IOException
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
    private var authTask: UserLoginTask? = null

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
                EditorInfo.IME_ACTION_SEND -> { attemptLogin(); true }
                else -> false
            }
        }

        btLogin.setOnClickListener { attemptLogin() }
    }

    override fun onStart() {
        super.onStart()
        analytics.setCurrentScreen(requireActivity(), "Login", null)
    }

    private fun attemptLogin() {
        log.info("Starting login.")

        if (authTask != null) {
            log.info("Aborting attempt, because another login task is currently running.")
            return
        }

        val imm = requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(txtUsername.windowToken, 0)
        imm.hideSoftInputFromWindow(txtPassword.windowToken, 0)

        // Reset errors.
        txtUsername.error = null
        txtPassword.error = null

        // Store values at the time of the LoginFragment attempt.
        val usernameStr = txtUsername.text.toString()
        val passwordStr = txtPassword.text.toString()

        var cancel = false
        var focusView: View? = null

        if (TextUtils.isEmpty(passwordStr)) {
            log.info("Cancelling login, because the password is empty.")
            analytics.logEvent("password_empty", null)
            txtPassword.error = getString(R.string.error_field_required)
            focusView = txtPassword
            cancel = true
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(usernameStr)) {
            log.info("Cancelling login, because the username is empty.")
            analytics.logEvent("username_empty", null)
            txtUsername.error = getString(R.string.error_field_required)
            focusView = txtUsername
            cancel = true
        } else if (!isUsernameValid(usernameStr)) {
            log.info("Cancelling login, because the username is invalid.")
            analytics.logEvent("username_invalid", null)
            txtUsername.error = getString(R.string.error_invalid_username)
            focusView = txtUsername
            cancel = true
        }

        if (cancel) {
            focusView?.requestFocus()
        } else {
            showProgress(true)
            val newAuthTask = UserLoginTask(usernameStr, passwordStr)
            newAuthTask.execute(null as Void?)
            authTask = newAuthTask
        }
    }

    private fun isUsernameValid(username: String): Boolean {
        return username.matches(phoneRegex) || username.matches(emailRegex)
    }

    private fun showProgress(show: Boolean) {
        login_form?.visibility = if (show) View.GONE else View.VISIBLE
        loading?.visibility = if (show) View.VISIBLE else View.GONE
    }

    enum class LoginStatus {
        Success,
        NoNetwork,
        HtmlChanged,
        AuthFailed
    }

    @SuppressLint("StaticFieldLeak")
    inner class UserLoginTask(
        private val username: String,
        private val password: String
    ) : AsyncTask<Void, Void, LoginStatus>() {

        override fun onPreExecute() {
            txtNoNetwork.visibility = View.GONE
            txtLoginFailed.visibility = View.GONE
        }

        override fun doInBackground(vararg params: Void): LoginStatus {
            log.info("Performing login.")
            analytics.logEvent("try_login", null)
            analytics.logEventOnce(requireContext(), "onb_try_login", null)
            val sessionId = try {
                log.info("Trying to obtain session ID using provided username and password.")
                coopLogin.login(username, password)
            } catch (e: IOException) {
                log.error("No network connection available.")
                analytics.logEvent("no_network", null)
                return NoNetwork
            } catch (e: HtmlChangedException) {
                log.error("HTML structure changed unexpectedly.", e)
                firebaseCrashlytics().recordException(e)
                return HtmlChanged
            }
            return if (sessionId != null) {
                log.info("Obtained session ID.")
                analytics.logEvent("auth_success", null)
                analytics.logEventOnce(requireContext(), "onb_auth_success", null)
                writeCredentials(requireContext(), username, password)
                writeSession(requireContext(), sessionId)
                Success
            } else {
                log.info("Did not receive any session ID.")
                analytics.logEvent("auth_failed", null)
                AuthFailed
            }
        }

        override fun onPostExecute(result: LoginStatus?) {
            authTask = null
            when (result) {
                Success ->
                    findNavController().navigate(R.id.action_login_to_status2)
                AuthFailed -> {
                    showProgress(false)
                    txtLoginFailed.visibility = View.VISIBLE
                }
                NoNetwork -> {
                    showProgress(false)
                    txtNoNetwork.visibility = View.VISIBLE
                }
                HtmlChanged -> {
                    showProgress(false)
                    Toast.makeText(context, R.string.update_necessary, Toast.LENGTH_LONG).show()
                }
            }
        }

        override fun onCancelled() {
            log.info("Login task cancelled.")
            authTask = null
            showProgress(false)
        }

    }

}
