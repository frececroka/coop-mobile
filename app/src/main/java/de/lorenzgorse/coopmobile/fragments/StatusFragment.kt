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
import kotlinx.android.synthetic.main.fragment_status.*
import kotlinx.coroutines.launch
import java.util.*

class StatusFragment: Fragment() {

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
        return inflater.inflate(R.layout.fragment_status, container, false)
    }

    override fun onStart() {
        super.onStart()
        analytics.setCurrentScreen(requireActivity(), "Status", null)
        viewModel.data.removeObservers(this)
        viewModel.data.observe(this, Observer(::setData))
        BalanceCheckWorker.enqueueIfEnabled(requireContext())
    }

    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.status, menu)
        return super.onCreateOptionsMenu(menu, menuInflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.itRefresh -> { refresh(); true }
            R.id.itAddOption -> { addOption(); true }
            R.id.itCorrespondences -> { viewCorrespondences(); true }
            R.id.itCombox -> { launchCombox(); true }
            R.id.itWebView -> { openWebView(); true }
            R.id.itLogout -> { logout(); true }
            R.id.itPreferences -> { preferences(); true }
            R.id.itOpenSource -> { openSource(); true }
            R.id.itRatingBanner -> { bannerRate.visibility = View.VISIBLE; true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refresh() {
        analytics.logEvent("refresh", null)
        viewModel.refresh()
    }

    private fun addOption() {
        findNavController().navigate(R.id.action_status_to_add_product)
    }

    private fun viewCorrespondences() {
        findNavController().navigate(R.id.action_status_to_correspondences)
    }

    private fun launchCombox() {
        lifecycleScope.launch { combox.launch() }
    }

    private fun openWebView() {
        findNavController().navigate(R.id.action_status_to_web_view)
    }

    private fun logout() {
        analytics.logEvent("logout", null)
        clearSession(requireContext())
        clearCredentials(requireContext())
        goToLogin()
    }

    private fun preferences() {
        findNavController().navigate(R.id.action_status_to_preferences)
    }

    private fun openSource() {
        lifecycleScope.launch { openSource.launch() }
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

        val credit = result.credit
        if (credit != null) {
            textCreditValue.text = String.format(Locale.GERMAN, "%.2f", credit.amount)
            textCreditUnit.text = credit.unit
            blkCredit.visibility = View.VISIBLE
        } else {
            blkCredit.visibility = View.GONE
        }

        consumptions.removeAllViews()
        result.consumptions.forEach {
            val consumption = layoutInflater.inflate(R.layout.consumption, consumptions, false)
            consumption.findViewById<TextView>(R.id.textTitle).text = it.description
            consumption.findViewById<TextView>(R.id.textValue).text = it.amount.toString()
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
        txtNoNetwork.visibility = View.VISIBLE
    }

    private fun showUpdateNecessary() {
        hideAll()
        layError.visibility = View.VISIBLE
        txtUpdate.visibility = View.VISIBLE
    }

    private fun showPlanUnsupported() {
        hideAll()
        layError.visibility = View.VISIBLE
        txtPlanUnsupported.visibility = View.VISIBLE
    }

    private fun hideAll() {
        layContent.visibility = View.GONE
        loading.visibility = View.GONE
        layError.visibility = View.GONE
        txtNoNetwork.visibility = View.GONE
        txtUpdate.visibility = View.GONE
        txtPlanUnsupported.visibility = View.GONE
    }

    private fun goToLogin() {
        findNavController().navigate(R.id.action_status_to_login)
    }

}

class CoopDataViewModel(
    app: Application
): ApiDataViewModel<CoopData>(app, { { it.getData() } })
