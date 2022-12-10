package de.lorenzgorse.coopmobile.ui.overview

import android.app.Application
import arrow.core.Either
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import de.lorenzgorse.coopmobile.State
import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.LabelledAmounts
import de.lorenzgorse.coopmobile.client.ProfileItem
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

    data class S(
        val consumption: List<LabelledAmounts>,
        val profile: Either<List<Pair<String, String>>, List<ProfileItem>>
    )

    val state: Flow<State<S, CoopError>> =
        liftFlow(
            load { client.getConsumption() },
            load {
                if (Firebase.remoteConfig.getBoolean("use_old_get_profile")) {
                    client.getProfile().map { Either.Left(it) }
                } else {
                    client.getProfileNoveau().map { Either.Right(it) }
                }
            }
        ) { cv, pv -> S(cv, pv) }.share()

}
