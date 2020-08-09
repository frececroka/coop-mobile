package de.lorenzgorse.coopmobile

import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.google.firebase.analytics.FirebaseAnalytics

class LoadDataErrorHandler(
    private val fragment: Fragment,
    private val goToLoginAction: Int
) {

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
        fragment.findNavController().popBackStack()
    }

    private fun showUpdateNecessary() {
        fragment.notify(R.string.update_necessary)
        fragment.findNavController().popBackStack()
    }

    private fun showPlanUnsupported() {
        fragment.notify(R.string.plan_unsupported)
        fragment.findNavController().popBackStack()
    }

    private fun goToLogin() {
        analytics.logEvent("go_to_login", null)
        fragment.findNavController().navigate(goToLoginAction)
    }

}
