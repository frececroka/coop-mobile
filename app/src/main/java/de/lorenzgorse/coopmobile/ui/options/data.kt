package de.lorenzgorse.coopmobile.ui.options

import android.app.Application
import de.lorenzgorse.coopmobile.State
import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.Product
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.data.CoopViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@ExperimentalCoroutinesApi
@FlowPreview
class OptionsData @Inject constructor(
    app: Application,
    client: CoopClient
) : CoopViewModel(app) {

    val state: Flow<State<List<Product>, CoopError>> =
        (load { client.getProducts() }).share()

}
