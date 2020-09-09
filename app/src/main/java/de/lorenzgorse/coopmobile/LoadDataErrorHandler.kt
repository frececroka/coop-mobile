package de.lorenzgorse.coopmobile

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.coopclient.CoopException

class LoadDataErrorHandler(private val fragment: Fragment) {

    private val context = fragment.requireContext()
    private val analytics = FirebaseAnalytics.getInstance(context)

    fun handle(error: LoadDataError) {
        handleLoadDataError(
            error,
            ::showNoNetwork,
            ::showUpdateNecessary,
            ::showPlanUnsupported,
            ::goToLogin)
    }

    private fun showNoNetwork() {
        fragment.notify(R.string.no_network)
        fragment.findNavController().navigate(R.id.action_overview)
    }

    private fun showUpdateNecessary(ex: CoopException.HtmlChanged) {
        fragment.notify(R.string.update_necessary)
        fragment.findNavController().navigate(R.id.action_overview)
    }

    private fun showPlanUnsupported() {
        fragment.notify(R.string.plan_unsupported)
        fragment.findNavController().navigate(R.id.action_overview)
    }

    private fun goToLogin() {
        analytics.logEvent("go_to_login", null)
        fragment.findNavController().navigate(R.id.action_login)
    }

}
