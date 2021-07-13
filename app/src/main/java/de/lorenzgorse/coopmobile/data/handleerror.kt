package de.lorenzgorse.coopmobile.data

import de.lorenzgorse.coopmobile.coopclient.CoopException

fun handleLoadDataError(
    error: LoadDataError,
    showNoNetwork: () -> Unit,
    showBadHtml: () -> Unit,
    showUpdateNecessary: (ex: CoopException.HtmlChanged) -> Unit,
    showPlanUnsupported: () -> Unit,
    goToLogin: () -> Unit
) {
    when (error) {
        is LoadDataError.NoNetwork -> showNoNetwork()
        is LoadDataError.BadHtml -> showBadHtml()
        is LoadDataError.HtmlChanged -> showUpdateNecessary(error.ex)
        is LoadDataError.PlanUnsupported -> showPlanUnsupported()
        is LoadDataError.NoClient -> goToLogin()
        is LoadDataError.Unauthorized -> goToLogin()
        is LoadDataError.FailedLogin -> goToLogin()
    }
}
