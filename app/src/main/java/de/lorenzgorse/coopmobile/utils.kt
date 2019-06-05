package de.lorenzgorse.coopmobile

fun handleLoadDataError(
    error: LoadDataError,
    showNoNetwork: () -> Unit,
    showUpdateNecessary: () -> Unit,
    showPlanUnsupported: () -> Unit,
    goToLogin: () -> Unit
) {
    when (error) {
        LoadDataError.NO_NETWORK -> showNoNetwork()
        LoadDataError.HTML_CHANGED -> showUpdateNecessary()
        LoadDataError.PLAN_UNSUPPORTED -> showPlanUnsupported()
        LoadDataError.NO_CLIENT -> goToLogin()
        LoadDataError.UNAUTHORIZED -> goToLogin()
        LoadDataError.FAILED_LOGIN -> goToLogin()
    }
}
