package de.lorenzgorse.coopmobile.ui.overview

import android.app.Application
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.*
import de.lorenzgorse.coopmobile.CoopModule.coopClientFactory
import de.lorenzgorse.coopmobile.components.EncryptedDiagnostics
import de.lorenzgorse.coopmobile.coopclient.CoopException
import de.lorenzgorse.coopmobile.coopclient.UnitValue
import de.lorenzgorse.coopmobile.data.ApiDataViewModel
import de.lorenzgorse.coopmobile.data.Value
import de.lorenzgorse.coopmobile.data.handleLoadDataError
import de.lorenzgorse.coopmobile.preferences.clearCredentials
import de.lorenzgorse.coopmobile.preferences.clearSession
import de.lorenzgorse.coopmobile.ui.debug.DebugMode
import kotlinx.android.synthetic.main.fragment_overview.*
import kotlinx.coroutines.launch
import java.util.*

class OverviewFragment: Fragment() {

    private lateinit var analytics: FirebaseAnalytics
    private lateinit var dataViewModel: CoopDataViewModel
    private lateinit var profileViewModel: CoopProfileViewModel
    private lateinit var combox: Combox
    private lateinit var openSource: OpenSource
    private lateinit var encryptedDiagnostics: EncryptedDiagnostics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        analytics = createAnalytics(requireContext())
        dataViewModel = ViewModelProvider(this).get(CoopDataViewModel::class.java)
        profileViewModel = ViewModelProvider(this).get(CoopProfileViewModel::class.java)
        combox = Combox(this)
        openSource = OpenSource(requireContext())
        encryptedDiagnostics = EncryptedDiagnostics(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_overview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bannerRate.activity = requireActivity()
        btGoToPlayStore.setOnClickListener { openPlayStore() }
    }

    override fun onStart() {
        super.onStart()
        analytics.setScreen("Overview")
        dataViewModel.data.removeObservers(this)
        dataViewModel.data.observe(this, Observer(::setData))
        profileViewModel.data.removeObservers(this)
        profileViewModel.data.observe(this, Observer(::setProfile))
        BalanceCheckWorker.enqueueIfEnabled(requireContext())
    }

    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.overview, menu)
        return super.onCreateOptionsMenu(menu, menuInflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val enabled = DebugMode.isEnabled(requireContext())
        menu.findItem(R.id.itDebug).isVisible = enabled
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.itRefresh -> { refresh(); true }
            R.id.itAddOption -> { addOption(); true }
            R.id.itCombox -> { launchCombox(); true }
            R.id.itLogout -> { logout(); true }
            R.id.itPreferences -> { preferences(); true }
            R.id.itOpenSource -> { openSource(); true }
            R.id.itDebug -> { debug(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refresh() {
        analytics.logEvent("refresh", null)
        dataViewModel.refresh()
    }

    private fun addOption() {
        findNavController().navigate(R.id.action_overview_to_add_product)
    }

    private fun launchCombox() {
        lifecycleScope.launch { combox.launch() }
    }

    private fun logout() {
        analytics.logEvent("logout", null)
        clearSession(requireContext())
        clearCredentials(requireContext())
        coopClientFactory.clear()
        goToLogin()
    }

    private fun preferences() {
        findNavController().navigate(R.id.action_overview_to_preferences)
    }

    private fun openSource() {
        lifecycleScope.launch { openSource.launch() }
    }

    private fun debug() {
        findNavController().navigate(R.id.action_overview_to_debug)
    }

    private fun setData(result: Value<List<UnitValue<Float>>>?) {
        when (result) {
            is Value.Initiated -> {
                loading.visibility = View.VISIBLE
                layError.visibility = View.GONE
                layContent.visibility = View.GONE
            }
            is Value.Failure -> handleLoadDataError(
                result.error,
                {showGenericError(R.drawable.ic_offline_bolt, R.string.no_network)},
                {showGenericError(R.drawable.ic_report, R.string.generic_error)},
                ::showUpdateNecessary,
                {showGenericError(R.drawable.ic_report, R.string.plan_unsupported)},
                ::goToLogin)
            is Value.Success -> onSuccess(result.value)
        }
    }

    private fun onSuccess(result: List<UnitValue<Float>>) {
        bannerRate.onLoadSuccess()

        showContent()

        consumptions.removeAllViews()
        result.forEach {
            val consumption = layoutInflater.inflate(R.layout.consumption, consumptions, false)
            val amount = if (it.amount.rem(1) <= 0.005) {
                it.amount.toInt().toString()
            } else {
                String.format(Locale.GERMAN, "%.2f", it.amount)
            }
            consumption.findViewById<TextView>(R.id.textTitle).text = it.description
            consumption.findViewById<TextView>(R.id.textValue).text = amount
            consumption.findViewById<TextView>(R.id.textUnit).text = it.unit
            consumptions.addView(consumption)
        }
    }

    private fun setProfile(result: Value<List<Pair<String, String>>>?) {
        profileProgress.visibility = View.GONE
        when (result) {
            is Value.Success -> setProfile(result.value)
        }
    }

    private fun setProfile(result: List<Pair<String, String>>) {
        profile.removeAllViews()
        result.forEach {
            val profileItem = layoutInflater.inflate(R.layout.profile_item, consumptions, false)
            profileItem.findViewById<TextView>(R.id.txtLabel).text = it.first
            profileItem.findViewById<TextView>(R.id.txtValue).text = it.second
            profile.addView(profileItem)
        }
    }

    private fun showContent() {
        hideAll()
        layContent.visibility = View.VISIBLE
    }

    private fun showGenericError(icon: Int, message: Int) {
        hideAll()
        imGenericError.setImageDrawable(requireContext().getDrawable(icon))
        txtGenericError.setText(message)
        layGenericError.visibility = View.VISIBLE
        layError.visibility = View.VISIBLE
    }

    private fun showUpdateNecessary(ex: CoopException.HtmlChanged) {
        hideAll()
        layError.visibility = View.VISIBLE
        layUpdate.visibility = View.VISIBLE
        val document = ex.document
        if (document != null && DebugMode.isEnabled(requireContext())) {
            btSendDiagnostics.visibility = View.VISIBLE
            btSendDiagnostics.setOnClickListener {
                btSendDiagnostics.isEnabled = false
                btSendDiagnostics.text = getString(R.string.diagnostics_uploading)
                lifecycleScope.launch {
                    val isSuccess = encryptedDiagnostics.send(document.outerHtml())
                    btSendDiagnostics?.text =
                        if (isSuccess) getString(R.string.diagnostics_upload_successful)
                        else getString(R.string.diagnostics_upload_failed)
                }
            }
        } else {
            btSendDiagnostics.visibility = View.GONE
        }
    }

    private fun hideAll() {
        layContent.visibility = View.GONE
        loading.visibility = View.GONE
        layError.visibility = View.GONE
        layGenericError.visibility = View.GONE
        layUpdate.visibility = View.GONE
    }

    private fun openPlayStore() {
        requireContext().openPlayStore()
    }

    private fun goToLogin() {
        findNavController().navigate(R.id.action_login)
    }

}

class CoopDataViewModel(
    app: Application
): ApiDataViewModel<List<UnitValue<Float>>>(app, { { it.getConsumption() } })

class CoopProfileViewModel(
    app: Application
): ApiDataViewModel<List<Pair<String, String>>>(app, { { it.getProfile() } })
