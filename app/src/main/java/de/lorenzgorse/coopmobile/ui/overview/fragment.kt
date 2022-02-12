package de.lorenzgorse.coopmobile.ui.overview

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.*
import de.lorenzgorse.coopmobile.client.UnitValue
import de.lorenzgorse.coopmobile.client.refreshing.CredentialsStore
import de.lorenzgorse.coopmobile.components.EncryptedDiagnostics
import de.lorenzgorse.coopmobile.data.data
import de.lorenzgorse.coopmobile.ui.RemoteDataView
import de.lorenzgorse.coopmobile.ui.debug.DebugMode
import kotlinx.android.synthetic.main.fragment_overview.*
import kotlinx.android.synthetic.main.remote_data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.*

@FlowPreview
@ExperimentalCoroutinesApi
class OverviewFragment : Fragment() {

    private lateinit var analytics: FirebaseAnalytics
    private lateinit var remoteDataView: RemoteDataView
    private lateinit var viewModel: OverviewData
    private lateinit var combox: Combox
    private lateinit var openSource: OpenSource
    private lateinit var encryptedDiagnostics: EncryptedDiagnostics
    private lateinit var credentialsStore: CredentialsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        analytics = createAnalytics(requireContext())
        viewModel = ViewModelProvider(this).get(OverviewData::class.java)
        combox = Combox(this)
        openSource = OpenSource(requireContext())
        encryptedDiagnostics = EncryptedDiagnostics(requireContext())
        credentialsStore = createCredentialsStore(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        remoteDataView = RemoteDataView.inflate(inflater, container, R.layout.fragment_overview)
        return remoteDataView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bannerRate.activity = requireActivity()
        btGoToPlayStore.setOnClickListener { openPlayStore() }
        remoteDataView.bindState(viewModel.state)
    }

    override fun onStart() {
        super.onStart()
        analytics.setScreen("Overview")
        BalanceCheckWorker.enqueueIfEnabled(requireContext())
        lifecycleScope.launch {
            viewModel.state.data().filterNotNull().collect { (consumption, profile) ->
                setConsumption(consumption)
                setProfile(profile)
            }
        }
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
            R.id.itRefresh -> {
                lifecycleScope.launch { viewModel.refresh() }; true
            }
            R.id.itAddOption -> {
                addOption(); true
            }
            R.id.itCombox -> {
                launchCombox(); true
            }
            R.id.itLogout -> {
                logout(); true
            }
            R.id.itPreferences -> {
                preferences(); true
            }
            R.id.itOpenSource -> {
                openSource(); true
            }
            R.id.itDebug -> {
                debug(); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun addOption() {
        findNavController().navigate(R.id.action_overview_to_add_product)
    }

    private fun launchCombox() {
        lifecycleScope.launch { combox.launch() }
    }

    private fun logout() {
        analytics.logEvent("logout", null)
        credentialsStore.clearSession()
        credentialsStore.clearCredentials()
        findNavController().navigate(R.id.action_login)
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

    private fun setConsumption(result: List<UnitValue<Float>>) {
        bannerRate.onLoadSuccess()

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

    private fun setProfile(result: List<Pair<String, String>>) {
        profile.removeAllViews()
        result.forEach {
            val profileItem = layoutInflater.inflate(R.layout.profile_item, consumptions, false)
            profileItem.findViewById<TextView>(R.id.txtLabel).text = it.first
            profileItem.findViewById<TextView>(R.id.txtValue).text = it.second
            profile.addView(profileItem)
        }
    }

    private fun openPlayStore() {
        requireContext().openPlayStore()
    }

}
