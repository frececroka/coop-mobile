package de.lorenzgorse.coopmobile.ui

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.data.LoadDataError
import de.lorenzgorse.coopmobile.data.handleLoadDataError

fun Fragment.handleLoadDataError(error: LoadDataError) {
    fun goToOverview() {
        findNavController().navigate(R.id.action_overview)
    }

    fun goToLogin() {
        val analytics = FirebaseAnalytics.getInstance(requireContext())
        analytics.logEvent("go_to_login", null)
        findNavController().navigate(R.id.action_login)
    }

    handleLoadDataError(
        error,
        ::goToOverview,
        ::goToOverview,
        {goToOverview()},
        ::goToOverview,
        ::goToLogin)
}
