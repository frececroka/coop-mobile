package de.lorenzgorse.coopmobile.ui.options

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import de.lorenzgorse.coopmobile.FirebaseAnalytics
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.client.Product
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.coopComponent
import de.lorenzgorse.coopmobile.data
import de.lorenzgorse.coopmobile.ui.AlertDialogBuilder
import de.lorenzgorse.coopmobile.ui.AlertDialogChoice
import de.lorenzgorse.coopmobile.ui.RemoteDataView
import kotlinx.android.synthetic.main.fragment_options.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import javax.inject.Inject

@FlowPreview
@ExperimentalCoroutinesApi
class OptionsFragment : Fragment() {

    private val log = LoggerFactory.getLogger(javaClass)

    @Inject lateinit var viewModel: OptionsData
    @Inject lateinit var analytics: FirebaseAnalytics
    @Inject lateinit var coopClient: CoopClient

    private lateinit var inflater: LayoutInflater
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var remoteDataView: RemoteDataView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coopComponent().inject(this)
        remoteConfig = FirebaseRemoteConfig.getInstance()
        inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        remoteDataView = RemoteDataView.inflate(inflater, container, R.layout.fragment_options)
        return remoteDataView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        remoteDataView.bindState(viewModel.state)
    }

    override fun onStart() {
        super.onStart()
        analytics.setScreen("Options")
        lifecycleScope.launch {
            viewModel.state.data().filterNotNull().collect { products ->
                setProducts(products)
            }
        }
    }

    private fun setProducts(result: List<Product>) {
        linProducts.removeAllViews()
        for (product in result) {
            log.info("Adding product ${product.name}")
            val productItemView = inflater.inflate(R.layout.product_item, linProducts, false)
            productItemView.findViewById<TextView>(R.id.txtName).text = product.name
            productItemView.findViewById<TextView>(R.id.txtPrice).text = product.price
            productItemView.findViewById<TextView>(R.id.txtDescription).text = product.description
            productItemView.findViewById<LinearLayout>(R.id.linProduct).setOnClickListener {
                lifecycleScope.launch { confirmBuyProduct(product) } }
            linProducts.addView(productItemView)
        }
    }

    private suspend fun confirmBuyProduct(product: Product) {
        val result = AlertDialogBuilder(requireContext())
            .setTitle(R.string.buy_confirm_title)
            .setMessage(resources.getString(R.string.buy_confirm_message, product.name, product.price))
            .setNegativeButton(R.string.no)
            .setPositiveButton(R.string.yes)
            .show()
        if (result == AlertDialogChoice.POSITIVE) {
            buyProduct(product)
        }
    }

    private fun buyProduct(product: Product) {
        val buyProduct = BuyProduct(this, coopClient)
        buyProduct.start(product.buySpec)
    }

}
