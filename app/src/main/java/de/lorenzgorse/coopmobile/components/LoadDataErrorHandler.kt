package de.lorenzgorse.coopmobile.components

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.data.LoadDataError
import de.lorenzgorse.coopmobile.data.handleLoadDataError

class LoadDataErrorHandler(private val fragment: Fragment) {

    private val context = fragment.requireContext()
    private val analytics = FirebaseAnalytics.getInstance(context)

    fun handle(error: LoadDataError) {
        handleLoadDataError(
            error,
            ::goToOverview,
            ::goToOverview,
            {goToOverview()},
            ::goToOverview,
            ::goToLogin)
    }

    private fun goToOverview() {
        fragment.findNavController().navigate(R.id.action_overview)
    }

    private fun goToLogin() {
        analytics.logEvent("go_to_login", null)
        fragment.findNavController().navigate(R.id.action_login)
    }

}
