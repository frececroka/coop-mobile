package de.lorenzgorse.coopmobile.ui.overview

import android.app.Application
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
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

    private val forceGenericConsumption: Boolean
        get() = Firebase.remoteConfig.getBoolean("use_generic_consumption")

    val state: Flow<State<Pair<List<UnitValue<Float>>, List<Pair<String, String>>>, CoopError>> =
        liftFlow(
            load { client.getConsumption() },
            load {
                val consumption = client.getConsumptionGeneric()
                when {
                    forceGenericConsumption -> consumption
                    else -> Either.Right(consumption.right())
                }
            },
            load { client.getProfile() }
        ) { cv, cvg, pv ->
            val useGenericConsumption = forceGenericConsumption || cv.isEmpty()
            when {
                cvg != null && useGenericConsumption -> Pair(cvg, pv)
                else -> Pair(cv, pv)
            }
        }.share()

}
