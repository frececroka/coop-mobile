package de.lorenzgorse.coopmobile.fragments

import android.app.Application
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.*
import de.lorenzgorse.coopmobile.LoadOnceLiveData.Value
import kotlinx.android.synthetic.main.fragment_status.*
import java.util.*

class StatusFragment: Fragment() {

    private lateinit var analytics: FirebaseAnalytics
    private lateinit var viewModel: CoopDataViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        analytics = createAnalytics(requireContext())
        viewModel = ViewModelProviders.of(this).get(CoopDataViewModel::class.java)
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
            R.id.itCredits -> { credits(); true }
            R.id.itLogout -> { logout(); true }
            R.id.itRatingBanner -> { bannerRate.visibility = View.VISIBLE; true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_status, container, false)
    }

    override fun onStart() {
        super.onStart()
        analytics.setCurrentScreen(requireActivity(), "Status", null)
        viewModel.data.removeObservers(this)
        viewModel.data.observe(this, Observer(::setData))
    }

    private fun setData(result: Value<CoopData>?) {
        when (result) {
            is Value.Initiated -> {
                loading.visibility = View.VISIBLE
                layError.visibility = View.GONE
                layContent.visibility = View.GONE
            }
            is Value.Failure -> handleLoadDataError(result.error, ::showNoNetwork, ::showUpdateNecessary, ::showPlanUnsupported, ::goToLogin)
            is Value.Success -> onSuccess(result.value)
        }
    }

    private fun showNoNetwork() {
        layContent.visibility = View.GONE
        loading.visibility = View.GONE
        layError.visibility = View.VISIBLE
        txtNoNetwork.visibility = View.VISIBLE
        txtUpdate.visibility = View.GONE
        txtPlanUnsupported.visibility = View.GONE
    }

    private fun showUpdateNecessary() {
        layContent.visibility = View.GONE
        loading.visibility = View.GONE
        layError.visibility = View.VISIBLE
        txtNoNetwork.visibility = View.GONE
        txtUpdate.visibility = View.VISIBLE
        txtPlanUnsupported.visibility = View.GONE
    }

    private fun showPlanUnsupported() {
        layContent.visibility = View.GONE
        loading.visibility = View.GONE
        layError.visibility = View.VISIBLE
        txtNoNetwork.visibility = View.GONE
        txtUpdate.visibility = View.GONE
        txtPlanUnsupported.visibility = View.VISIBLE
    }

    private fun onSuccess(result: CoopData) {
        analytics.logEvent("loaded_data", null)
        analytics.logEventOnce(requireContext(), "onb_loaded_data", null)

        bannerRate.onLoadSuccess()

        layContent.visibility = View.VISIBLE
        loading.visibility = View.GONE
        layError.visibility = View.GONE

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

    private fun credits() {
        analytics.logEvent("credits", null)
        findNavController().navigate(R.id.action_status_to_credits)
    }

    private fun logout() {
        analytics.logEvent("logout", null)
        clearSession(requireContext())
        clearCredentials(requireContext())
        goToLogin()
    }

    private fun refresh() {
        analytics.logEvent("refresh", null)
        viewModel.data.refresh()
    }

    private fun addOption() {
        findNavController().navigate(R.id.action_status_to_add_product3)
    }

    private fun viewCorrespondences() {
        findNavController().navigate(R.id.action_status_to_correspondences)
    }

    private fun goToLogin() {
        findNavController().navigate(R.id.action_status_to_login2)
    }

}

class CoopDataViewModel(private val app: Application): AndroidViewModel(app) {

    val data: LoadOnceLiveData<CoopData> by lazy {
        liveData<Void, CoopData>(app.applicationContext) { it.getData() } }

}
