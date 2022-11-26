package de.lorenzgorse.coopmobile.ui.overview

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import de.lorenzgorse.coopmobile.*
import de.lorenzgorse.coopmobile.client.LabelledAmounts
import de.lorenzgorse.coopmobile.client.refreshing.CredentialsStore
import de.lorenzgorse.coopmobile.components.EncryptedDiagnostics
import de.lorenzgorse.coopmobile.ui.RemoteDataView
import kotlinx.android.synthetic.main.fragment_overview.*
import kotlinx.android.synthetic.main.remote_data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@FlowPreview
@ExperimentalCoroutinesApi
class OverviewFragment : Fragment(), MenuProvider {

    @Inject
    lateinit var viewModel: OverviewData

    @Inject
    lateinit var credentialsStore: CredentialsStore

    @Inject
    lateinit var encryptedDiagnostics: EncryptedDiagnostics

    @Inject
    lateinit var analytics: FirebaseAnalytics

    private lateinit var remoteDataView: RemoteDataView
    private lateinit var combox: Combox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coopComponent().inject(this)
        (activity as MenuHost).addMenuProvider(this, this, Lifecycle.State.STARTED)
        combox = Combox(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        remoteDataView = RemoteDataView.inflate(inflater, container, R.layout.fragment_overview)
        return remoteDataView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bannerRate.activity = requireActivity()
        btGoToPlayStore.setOnClickListener { openPlayStore() }
        remoteDataView.bindState(viewModel.state)
    }

    override fun onStart() {
        super.onStart()
        analytics.setScreen("Overview")
        lifecycleScope.launch {
            viewModel.state.data().filterNotNull().collect { (consumption, profile) ->
                setConsumption(consumption)
                setProfile(profile)
            }
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.overview, menu)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.itRefresh -> {
                lifecycleScope.launch { viewModel.refresh() }; true
            }
            R.id.itCombox -> {
                // TODO: move to NavHost
                launchCombox(); true
            }
            else -> false
        }
    }

    private fun launchCombox() {
        analytics.logEvent("Combox", null)
        lifecycleScope.launch { combox.launch() }
    }

    private fun setConsumption(result: List<LabelledAmounts>) {
        // TODO: why is this null for some users?
        bannerRate?.onLoadSuccess()

        analytics.logEvent("ConsumptionViewsCleared", null)
        consumptions.removeAllViews()
        result.forEach {
            createConsumptionView(it).ifPresent(consumptions::addView)
        }
    }

    private fun createConsumptionView(labelledAmounts: LabelledAmounts): Optional<View> {
        val consumption = layoutInflater.inflate(R.layout.consumption, consumptions, false)
        val textTitle = consumption.findViewById<TextView>(R.id.textTitle)
        val textValue = consumption.findViewById<TextView>(R.id.textValue)
        val textUnit = consumption.findViewById<TextView>(R.id.textUnit)

        val labelledAmount =
            labelledAmounts.labelledAmounts.firstOrNull() ?: return Optional.empty()
        val amount = labelledAmount.amount

        textTitle.text = labelledAmounts.description

        if (amount.value.isInfinite()) {
            textValue.text = getString(R.string.unlimited)
            textUnit.visibility = View.GONE
        } else {
            textValue.text = formatFiniteValue(amount.value)
            textUnit.text = amount.unit
        }

        analytics.logEvent(
            "ConsumptionView",
            bundleOf(
                "Description" to textTitle.text,
                "Unit" to textUnit.text,
                "UnitVisibility" to textUnit.visibility
            )
        )

        return Optional.of(consumption)
    }

    private fun formatFiniteValue(value: Double) =
        if (value.rem(1) <= 0.005) {
            value.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.2f", value)
        }

    private fun setProfile(result: List<Pair<String, String>>) {
        profile.removeAllViews()
        result.forEach {
            val profileItem = layoutInflater.inflate(R.layout.profile_item, consumptions, false)
            profileItem.findViewById<TextView>(R.id.txtLabel).text = it.first
            profileItem.findViewById<TextView>(R.id.txtValue).text = it.second
            profile.addView(profileItem)
        }
    }

    private fun openPlayStore() {
        requireContext().openPlayStore()
    }

}
