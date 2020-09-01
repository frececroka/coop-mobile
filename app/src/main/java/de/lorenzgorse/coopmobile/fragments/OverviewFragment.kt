package de.lorenzgorse.coopmobile.fragments

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
import de.lorenzgorse.coopmobile.coopclient.CoopData
import kotlinx.android.synthetic.main.fragment_overview.*
import kotlinx.coroutines.launch
import java.util.*

class OverviewFragment: Fragment() {

    private lateinit var analytics: FirebaseAnalytics
    private lateinit var viewModel: CoopDataViewModel
    private lateinit var combox: Combox
    private lateinit var openSource: OpenSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        analytics = createAnalytics(requireContext())
        viewModel = ViewModelProvider(this).get(CoopDataViewModel::class.java)
        combox = Combox(this)
        openSource = OpenSource(requireContext())
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
        viewModel.data.removeObservers(this)
        viewModel.data.observe(this, Observer(::setData))
        BalanceCheckWorker.enqueueIfEnabled(requireContext())
    }

    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.overview, menu)
        return super.onCreateOptionsMenu(menu, menuInflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val enabled = DebugFragment.isEnabled(requireContext())
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
        viewModel.refresh()
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

    private fun setData(result: Value<CoopData>?) {
        when (result) {
            is Value.Initiated -> {
                loading.visibility = View.VISIBLE
                layError.visibility = View.GONE
                layContent.visibility = View.GONE
            }
            is Value.Failure -> handleLoadDataError(
                result.error,
                ::showNoNetwork,
                ::showUpdateNecessary,
                ::showPlanUnsupported,
                ::goToLogin)
            is Value.Success -> onSuccess(result.value)
        }
    }

    private fun onSuccess(result: CoopData) {
        bannerRate.onLoadSuccess()

        showContent()

        consumptions.removeAllViews()
        result.items.forEach {
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

    private fun showContent() {
        hideAll()
        layContent.visibility = View.VISIBLE
    }

    private fun showNoNetwork() {
        hideAll()
        layError.visibility = View.VISIBLE
        layNoNetwork.visibility = View.VISIBLE
    }

    private fun showUpdateNecessary() {
        hideAll()
        layError.visibility = View.VISIBLE
        layUpdate.visibility = View.VISIBLE
    }

    private fun showPlanUnsupported() {
        hideAll()
        layError.visibility = View.VISIBLE
        layPlanUnsupported.visibility = View.VISIBLE
    }

    private fun hideAll() {
        layContent.visibility = View.GONE
        loading.visibility = View.GONE
        layError.visibility = View.GONE
        layNoNetwork.visibility = View.GONE
        layUpdate.visibility = View.GONE
        layPlanUnsupported.visibility = View.GONE
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
): ApiDataViewModel<CoopData>(app, { { it.getData() } })
