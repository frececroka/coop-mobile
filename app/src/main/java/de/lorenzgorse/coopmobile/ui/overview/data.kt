package de.lorenzgorse.coopmobile.ui.overview

import android.app.Application
import de.lorenzgorse.coopmobile.coopclient.UnitValue
import de.lorenzgorse.coopmobile.data.CoopError
import de.lorenzgorse.coopmobile.data.CoopViewModel
import de.lorenzgorse.coopmobile.data.State
import de.lorenzgorse.coopmobile.data.liftFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@FlowPreview
@ExperimentalCoroutinesApi
class OverviewData(app: Application) : CoopViewModel(app) {

    val state: Flow<State<Pair<List<UnitValue<Float>>, List<Pair<String, String>>>, CoopError>> =
        liftFlow(
            load { it.getConsumption() },
            load { it.getProfile() }
        ) { cv, pv -> Pair(cv, pv) }.share()

}
