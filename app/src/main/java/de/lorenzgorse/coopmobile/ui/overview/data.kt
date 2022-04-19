package de.lorenzgorse.coopmobile.ui.overview

import android.app.Application
import de.lorenzgorse.coopmobile.State
import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.Either
import de.lorenzgorse.coopmobile.client.UnitValue
import de.lorenzgorse.coopmobile.data.CoopViewModel
import de.lorenzgorse.coopmobile.liftFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@FlowPreview
@ExperimentalCoroutinesApi
class OverviewData(app: Application) : CoopViewModel(app) {

    val state: Flow<State<Pair<List<UnitValue<Float>>, List<Pair<String, String>>>, CoopError>> =
        liftFlow(
            load { client.getConsumption() },
            load { Either.Right(client.getConsumptionGeneric().right()) },
            load { client.getProfile() }
        ) { cv, _, pv -> Pair(cv, pv) }.share()

}
