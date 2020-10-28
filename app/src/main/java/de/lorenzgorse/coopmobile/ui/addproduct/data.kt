package de.lorenzgorse.coopmobile.ui.addproduct

import android.app.Application
import de.lorenzgorse.coopmobile.coopclient.Product
import de.lorenzgorse.coopmobile.data.CoopError
import de.lorenzgorse.coopmobile.data.CoopViewModel
import de.lorenzgorse.coopmobile.data.State
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@ExperimentalCoroutinesApi
@FlowPreview
class AddProductData(app: Application) : CoopViewModel(app) {

    val state: Flow<State<List<Product>, CoopError>> = load { it.getProducts() }.share()

}
