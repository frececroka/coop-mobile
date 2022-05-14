package de.lorenzgorse.coopmobile.ui.overview

import android.app.Application
import de.lorenzgorse.coopmobile.State
import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.LabelledAmount
import de.lorenzgorse.coopmobile.client.LabelledAmounts
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.data.CoopViewModel
import de.lorenzgorse.coopmobile.liftFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@FlowPreview
@ExperimentalCoroutinesApi
class OverviewData @Inject constructor(
    app: Application,
    client: CoopClient
) : CoopViewModel(app) {

    val state: Flow<State<Pair<List<LabelledAmounts>, List<Pair<String, String>>>, CoopError>> =
        liftFlow(
            load { client.getConsumption() },
            load { client.getProfile() }
        ) { cv, pv -> Pair(cv, pv) }.share()

}
