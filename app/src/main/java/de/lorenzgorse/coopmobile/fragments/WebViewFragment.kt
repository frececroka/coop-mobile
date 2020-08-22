package de.lorenzgorse.coopmobile.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.CoopClient
import de.lorenzgorse.coopmobile.CoopModule.coopClientFactory
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.createAnalytics
import de.lorenzgorse.coopmobile.determineCountry
import kotlinx.android.synthetic.main.fragment_web_view.*
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

class WebViewFragment : Fragment() {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private lateinit var inflater: LayoutInflater
    private lateinit var analytics: FirebaseAnalytics

    private var lastLogin: Date? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        analytics = createAnalytics(requireContext())
        inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_web_view, container, false)
    }

    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.web_view, menu)
        return super.onCreateOptionsMenu(menu, menuInflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        fun logNavigationEvent(target: String) {
            analytics.logEvent("web_view_navigate", bundleOf("target" to target))
        }
        return when (item.itemId) {
            R.id.itAddOption -> {
                logNavigationEvent("add_product")
                webView.loadUrl("https://myaccount.coopmobile.ch/eCare/prepaid/de/add_product")
                true
            }
            R.id.itCorrespondences -> {
                logNavigationEvent("my_correspondence")
                webView.loadUrl("https://myaccount.coopmobile.ch/eCare/prepaid/de/my_correspondence/index")
                true
            }
            R.id.itTopUp -> {
                logNavigationEvent("my_topup")
                webView.loadUrl("https://myaccount.coopmobile.ch/eCare/prepaid/de/my_topup")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onStart() {
        super.onStart()
        analytics.setCurrentScreen(requireActivity(), "WebView", null)
        analytics.logEvent("web_view", null)

        // Initialize the web view.
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = MyWebViewClient()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val webViewBundle = savedInstanceState?.getBundle("webView")
        if (webViewBundle == null) {
            lifecycleScope.launch { loadCockpit() }
        } else {
            webView.restoreState(webViewBundle)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val webViewBundle = Bundle()
        webView.saveState(webViewBundle)
        outState.putBundle("webView", webViewBundle)
    }

    private suspend fun loadCockpit() {
        val coopClient = coopClientFactory.get(requireContext())
        if (coopClient == null) {
            // There is a problem with authentication, bail out and send the user to the login
            // screen.
            findNavController().navigate(R.id.action_login)
            return
        }

        loadCockpit(coopClient)
    }

    private fun loadCockpit(coopClient: CoopClient) {
        // Setup the session cookie.
        val sessionId = coopClient.sessionId()
        CookieManager.getInstance().setCookie(
            "https://myaccount.coopmobile.ch/",
            "_ecare_session=$sessionId")

        // Show web view, because we have a session. It may have been hidden earlier if the session
        // expired.
        loading.visibility = View.GONE
        webView.visibility = View.VISIBLE

        // Load the account cockpit.
        val country = determineCountry()
        webView.loadUrl("https://myaccount.coopmobile.ch/eCare/prepaid/$country")
    }

    inner class MyWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            val url = request?.url
            log.info("Loading url $url")

            return if (url != null && url.toString().endsWith("/users/sign_in")) {
                log.info("The current session is invalid. Cancelling load and refreshing session.")

                // Hide web view while we don't have a valid session.
                loading?.visibility = View.VISIBLE
                webView?.visibility = View.GONE

                // Try to get a new session.
                lifecycleScope.launch { refreshSession() }

                // Cancel load.
                true
            } else {
                super.shouldOverrideUrlLoading(view, request)
            }
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            progressBar?.progress = 0
            progressBar?.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            progressBar?.visibility = View.GONE
        }

        override fun onLoadResource(view: WebView?, url: String?) {
            super.onLoadResource(view, url)
            if (progressBar == null) return
            val max = progressBar.max
            val current = progressBar.progress
            progressBar.progress = current + (max - current)/10
        }
    }

    private suspend fun refreshSession() {
        val timeSinceLastLogin = timeSinceLastLogin()
        if (timeSinceLastLogin != null && timeSinceLastLogin < 10_000) {
            // We already tried to login too recently, this indicates a problem with
            // authentication. Bail out and send the user to the login screen.
            findNavController().navigate(R.id.action_login)
            return
        }

        // We will try to login now.
        lastLogin = Date()

        val coopClient = try {
            coopClientFactory.refresh(requireContext(), true)
        } catch (e: IOException) {
            // TODO: Show network error.
            return
        }

        if (coopClient == null) {
            // We couldn't refresh the session, this indicates a problem with authentication. Bail
            // out and send the user to the login screen.
            findNavController().navigate(R.id.action_login)
            return
        }

        // Try to load the cockpit with the refreshed session.
        loadCockpit(coopClient)
    }

    private fun timeSinceLastLogin(): Long? {
        val lastFailure = lastLogin ?: return null
        return Date().time - lastFailure.time
    }

}
