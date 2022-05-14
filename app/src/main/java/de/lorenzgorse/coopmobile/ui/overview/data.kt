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

    val state: Flow<State<Pair<List<LabelledAmount>, List<Pair<String, String>>>, CoopError>> =
        liftFlow(
            load { client.getConsumption() },
            load { client.getProfile() }
        ) { cv, pv ->
            Pair(shoehornLabelledAmounts(cv), pv)
        }.share()

    // We get a list of LabelledAmounts from getConsumption, which is more comprehensive than the
    // list of LabelledAmount we got before. The UI can't display LabelledAmounts values yet, so we
    // shoehorn them into LabelledAmount values.
    private fun shoehornLabelledAmounts(labelledAmounts: List<LabelledAmounts>): List<LabelledAmount> =
        labelledAmounts.mapNotNull { shoehornLabelledAmounts(it) }

    private fun shoehornLabelledAmounts(labelledAmounts: LabelledAmounts): LabelledAmount? {
        val labelledAmount = labelledAmounts.labelledAmounts.firstOrNull() ?: return null
        // The description of the labelledAmounts is more useful than the description of an
        // individual labelledAmount.
        return labelledAmount.copy(description = labelledAmounts.description)
    }

}
