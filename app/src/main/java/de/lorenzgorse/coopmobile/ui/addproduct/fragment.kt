package de.lorenzgorse.coopmobile.ui.addproduct

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import de.lorenzgorse.coopmobile.*
import de.lorenzgorse.coopmobile.coopclient.Product
import de.lorenzgorse.coopmobile.data.ApiDataViewModel
import de.lorenzgorse.coopmobile.data.Value
import de.lorenzgorse.coopmobile.ui.AlertDialogBuilder
import de.lorenzgorse.coopmobile.ui.AlertDialogChoice
import de.lorenzgorse.coopmobile.ui.handleLoadDataError
import kotlinx.android.synthetic.main.fragment_add_product.*
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class AddProductFragment : Fragment() {

    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var inflater: LayoutInflater
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var analytics: FirebaseAnalytics
    private lateinit var viewModel: ProductsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics = createAnalytics(requireContext())
        remoteConfig = FirebaseRemoteConfig.getInstance()
        remoteConfig.fetchAndActivate()
        inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        viewModel = ViewModelProvider(this).get(ProductsViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_product, container, false)
    }

    override fun onStart() {
        super.onStart()
        analytics.setScreen("AddProduct")
        viewModel.data.observe(this, Observer(::setData))
    }

    private fun setData(result: Value<List<Product>>?) {
        when (result) {
            is Value.Initiated -> {
                loading.visibility = View.VISIBLE
                layContent.visibility = View.GONE
            }
            is Value.Failure ->
                handleLoadDataError(result.error)
            is Value.Success ->
                onSuccess(result.value)
        }
    }

    private fun onSuccess(result: List<Product>) {
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
        loading.visibility = View.GONE
        layContent.visibility = View.VISIBLE
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

class ProductsViewModel(
    app: Application
) : ApiDataViewModel<List<Product>>(app, { { it.getProducts() } })
