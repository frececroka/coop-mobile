package de.lorenzgorse.coopmobile.ui.addproduct

import android.app.Application
import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.Product
import de.lorenzgorse.coopmobile.createClient
import de.lorenzgorse.coopmobile.data.CoopViewModel
import de.lorenzgorse.coopmobile.data.State
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@ExperimentalCoroutinesApi
@FlowPreview
class AddProductData(app: Application) : CoopViewModel(app) {

    private val client = createClient(app)

    val state: Flow<State<List<Product>, CoopError>> =
        (load { client.getProducts() }).share()

}
