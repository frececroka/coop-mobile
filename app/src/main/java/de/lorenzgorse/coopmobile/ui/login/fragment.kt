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
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import de.lorenzgorse.coopmobile.*
import de.lorenzgorse.coopmobile.client.refreshing.CredentialsStore
import de.lorenzgorse.coopmobile.client.simple.CoopException.HtmlChanged
import de.lorenzgorse.coopmobile.client.simple.CoopLogin
import de.lorenzgorse.coopmobile.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import javax.inject.Inject

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

    @Inject lateinit var coopLogin: CoopLogin
    @Inject lateinit var credentialsStore: CredentialsStore
    @Inject lateinit var testAccounts: TestAccounts
    @Inject lateinit var analytics: FirebaseAnalytics

    private lateinit var binding: FragmentLoginBinding

    private val loginInProgress = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coopComponent().inject(this)
        if (credentialsStore.loadCredentials() != null) {
            findNavController().navigate(R.id.action_overview)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLoginBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        for (textView in listOf(binding.txtPrivacyPolicy, binding.txtLoginFailed)) {
            textView.text = HtmlCompat.fromHtml(textView.text.toString(), FROM_HTML_MODE_COMPACT)
            textView.movementMethod = LinkMovementMethod.getInstance()
        }

        binding.txtPassword.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_SEND -> {
                    lifecycleScope.launch { attemptLoginGuard() }; true
                }
                else -> false
            }
        }

        binding.btLogin.setOnClickListener {
            lifecycleScope.launch { attemptLoginGuard() }
        }
    }

    override fun onStart() {
        super.onStart()
        analytics.setScreen("Login")
    }

    private suspend fun attemptLoginGuard() {
        log.info("Starting login.")

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
        analytics.logEvent("Login_Attempt", null)

        binding.cardError.visibility = View.GONE
        binding.txtNoNetwork.visibility = View.GONE
        binding.txtLoginFailed.visibility = View.GONE

        val imm = requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.txtUsername.windowToken, 0)
        imm.hideSoftInputFromWindow(binding.txtPassword.windowToken, 0)

        // Reset errors.
        binding.txtUsername.error = null
        binding.txtPassword.error = null

        // Store values at the time of the LoginFragment attempt.
        val username = binding.txtUsername.text.toString()
        val password = binding.txtPassword.text.toString()

        var cancel: String? = null
        var focusView: View? = null

        if (TextUtils.isEmpty(password)) {
            binding.txtPassword.error = getString(R.string.error_field_required)
            focusView = binding.txtPassword
            cancel = "Login_PasswordEmpty"
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(username)) {
            binding.txtUsername.error = getString(R.string.error_field_required)
            focusView = binding.txtUsername
            cancel = "Login_UsernameEmpty"
        } else if (!isUsernameValid(username)) {
            binding.txtUsername.error = getString(R.string.error_invalid_username)
            focusView = binding.txtUsername
            cancel = "Login_UsernameInvalid"
        }

        if (cancel != null) {
            log.info("Cancelling login: $cancel")
            analytics.logEvent("Login_InputError", bundleOf("Reason" to cancel))
            focusView?.requestFocus()
            return
        }

        val cleanedUsername =
            if (isPhoneNumber(username)) {
                username.replace(Regex("\\s"), "")
            } else {
                username
            }

        if (testAccounts.isTestAccount(cleanedUsername)) {
            testAccounts.activate()
        } else {
            testAccounts.deactivate()
        }

        // TODO: this is hideous
        // Recreate dependencies, since the CoopLogin implementation
        // depends on the (variable) test account mode
        app().recreateComponent()
        coopComponent().inject(this)

        showProgress(true)

        log.info("Performing login.")

        val sessionId = try {
            coopLogin.login(cleanedUsername, password, CoopLogin.Origin.Manual)
        } catch (e: IOException) {
            log.error("No network connection available.", e)
            showProgress(false)
            binding.cardError.visibility = View.VISIBLE
            binding.txtNoNetwork.visibility = View.VISIBLE
            return
        } catch (e: HtmlChanged) {
            log.error("HTML structure changed unexpectedly.", e)
            showProgress(false)
            Toast.makeText(context, R.string.update_necessary, Toast.LENGTH_LONG).show()
            return
        }

        return if (sessionId != null) {
            log.info("Obtained session ID.")
            credentialsStore.setCredentials(cleanedUsername, password)
            credentialsStore.setSession(sessionId)
            findNavController().navigate(R.id.action_overview)
        } else {
            log.info("Did not receive any session ID.")
            showProgress(false)
            binding.cardError.visibility = View.VISIBLE
            binding.txtLoginFailed.visibility = View.VISIBLE
        }
    }

    private fun isUsernameValid(username: String): Boolean {
        return username.matches(phoneRegex) || username.matches(emailRegex)
    }

    private fun isPhoneNumber(username: String): Boolean =
        username.matches(phoneRegex)

    private fun showProgress(show: Boolean) {
        binding.txtUsername.isEnabled = !show
        binding.txtPassword.isEnabled = !show
        binding.btLogin.isEnabled = !show
        binding.loginProgressIndicator.visibility = if (show) View.VISIBLE else View.INVISIBLE
    }

}
