package de.lorenzgorse.coopmobile.ui.overview

import android.app.Application
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import de.lorenzgorse.coopmobile.State
import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.Either
import de.lorenzgorse.coopmobile.client.UnitValue
import de.lorenzgorse.coopmobile.client.UnitValueBlock
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
            load { client.getConsumptionGeneric() },
            load { client.getProfile() }
        ) { cv, pv ->
            Pair(shoehornUnitValueBlocks(cv), pv)
        }.share()

    // We get a list of UnitValueBlock from getConsumptionGeneric, which is more comprehensive
    // than the list of UnitValue we get from getConsumption. The UI can't display UnitValueBlock
    // values yet, so we shoehorn them into UnitValue values.
    private fun shoehornUnitValueBlocks(unitValueBlocks: List<UnitValueBlock>): List<UnitValue<Float>> =
        unitValueBlocks.mapNotNull { shoehornUnitValueBlock(it) }

    private fun shoehornUnitValueBlock(unitValueBlock: UnitValueBlock): UnitValue<Float>? {
        val unitValue = unitValueBlock.unitValues.firstOrNull() ?: return null
        // The description of the unitValueBlock is more useful than the description of an
        // individual unitValue.
        return unitValue.copy(description = unitValueBlock.description)
    }

}
