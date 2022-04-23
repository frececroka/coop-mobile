package de.lorenzgorse.coopmobile.ui.addproduct

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.client.Product
import de.lorenzgorse.coopmobile.createAnalytics
import de.lorenzgorse.coopmobile.data
import de.lorenzgorse.coopmobile.setScreen
import de.lorenzgorse.coopmobile.ui.AlertDialogBuilder
import de.lorenzgorse.coopmobile.ui.AlertDialogChoice
import de.lorenzgorse.coopmobile.ui.RemoteDataView
import kotlinx.android.synthetic.main.fragment_add_product.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

@FlowPreview
@ExperimentalCoroutinesApi
class AddProductFragment : Fragment() {

    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var inflater: LayoutInflater
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var analytics: FirebaseAnalytics
    private lateinit var remoteDataView: RemoteDataView
    private lateinit var viewModel: AddProductData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics = createAnalytics(requireContext())
        remoteConfig = FirebaseRemoteConfig.getInstance()
        inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        viewModel = ViewModelProvider(this).get(AddProductData::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        remoteDataView = RemoteDataView.inflate(inflater, container, R.layout.fragment_add_product)
        return remoteDataView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        remoteDataView.bindState(viewModel.state)
    }

    override fun onStart() {
        super.onStart()
        analytics.setScreen("AddProduct")
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
        if (remoteConfig.getBoolean("buy_option_enabled")) {
            val result = AlertDialogBuilder(requireContext())
                .setTitle(R.string.buy_confirm_title)
                .setMessage(resources.getString(R.string.buy_confirm_message, product.name, product.price))
                .setNegativeButton(R.string.no)
                .setPositiveButton(R.string.yes)
                .show()
            if (result == AlertDialogChoice.POSITIVE) {
                val data = bundleOf("product" to product.buySpec)
                findNavController().navigate(R.id.action_add_product_to_buy_product, data)
            }
        } else {
            AlertDialogBuilder(requireContext())
                .setMessage(R.string.buy_option_not_available)
                .setNeutralButton(R.string.okay)
                .show()
        }
    }

}
