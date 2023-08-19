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
import arrow.core.Either
import de.lorenzgorse.coopmobile.*
import de.lorenzgorse.coopmobile.client.AmountUnit
import de.lorenzgorse.coopmobile.client.LabelledAmounts
import de.lorenzgorse.coopmobile.client.ProfileItem
import de.lorenzgorse.coopmobile.client.refreshing.CredentialsStore
import de.lorenzgorse.coopmobile.components.EncryptedDiagnostics
import de.lorenzgorse.coopmobile.databinding.FragmentOverviewBinding
import de.lorenzgorse.coopmobile.ui.RemoteDataView
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
    private lateinit var binding: FragmentOverviewBinding

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
        binding = FragmentOverviewBinding.bind(remoteDataView.contentView)
        return remoteDataView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.bannerRate.activity = requireActivity()
        remoteDataView.bindState(viewModel.state)
    }

    override fun onStart() {
        super.onStart()
        analytics.setScreen("Overview")
        lifecycleScope.launch {
            viewModel.state.data().filterNotNull().collect { (consumption, profile) ->
                setConsumption(consumption)
                when (profile) {
                    is Either.Left -> setProfile(profile.value)
                    is Either.Right -> setProfileNoveau(profile.value)
                }
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
        binding.bannerRate.onLoadSuccess()

        analytics.logEvent("ConsumptionViewsCleared", null)
        binding.consumptions.removeAllViews()
        result.forEach {
            createConsumptionView(it).ifPresent(binding.consumptions::addView)
        }
    }

    private fun createConsumptionView(labelledAmounts: LabelledAmounts): Optional<View> {
        val consumption = layoutInflater.inflate(R.layout.consumption, binding.consumptions, false)
        val textTitle = consumption.findViewById<TextView>(R.id.textTitle)
        val textValue = consumption.findViewById<TextView>(R.id.textValue)
        val textUnit = consumption.findViewById<TextView>(R.id.textUnit)

        val labelledAmount =
            labelledAmounts.labelledAmounts.firstOrNull() ?: return Optional.empty()
        val amount = labelledAmount.amount

        textTitle.text = getLabelledAmountsDescription(labelledAmounts)

        if (amount.value.isInfinite()) {
            textValue.text = getString(R.string.unlimited)
            textUnit.visibility = View.GONE
        } else {
            textValue.text = formatFiniteValue(amount.value)
            textUnit.text = amount.unit?.let { formatUnit(it) }
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

    private fun getLabelledAmountsDescription(labelledAmounts: LabelledAmounts): String {
        val descriptionStringId = when (labelledAmounts.kind) {
            LabelledAmounts.Kind.Credit -> R.string.labelled_amount_credit
            LabelledAmounts.Kind.DataSwitzerland -> R.string.labelled_amount_data_switzerland
            LabelledAmounts.Kind.DataEurope -> R.string.labelled_amount_data_europe
            LabelledAmounts.Kind.DataSwitzerlandAndEurope -> R.string.labelled_amount_data_switzerland_and_europe
            LabelledAmounts.Kind.CallsAndSmsSwitzerland -> R.string.labelled_amount_calls_and_sms_switzerland
            LabelledAmounts.Kind.OptionsAndCalls -> R.string.labelled_amount_options_and_calls
            LabelledAmounts.Kind.Unknown -> null
        }
        return if (descriptionStringId != null) {
            getString(descriptionStringId)
        } else {
            labelledAmounts.description
        }
    }

    private fun formatFiniteValue(value: Double) =
        if (value.rem(1) <= 0.005) {
            value.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.2f", value)
        }

    private fun formatUnit(unit: AmountUnit): String {
        val unitStringId = when (unit.kind) {
            AmountUnit.Kind.CHF -> R.string.unit_chf
            AmountUnit.Kind.Minutes -> R.string.unit_minutes
            AmountUnit.Kind.Units -> R.string.unit_units
            AmountUnit.Kind.GB -> R.string.unit_gb
            AmountUnit.Kind.MB -> R.string.unit_mb
            AmountUnit.Kind.Unlimited -> R.string.unit_unlimited
            AmountUnit.Kind.Unknown -> return unit.source
        }
        return getString(unitStringId)
    }

    private fun setProfile(result: List<Pair<String, String>>) {
        binding.profile.removeAllViews()
        result.forEach {
            addProfileItem(it.first, it.second)
        }
    }

    private fun setProfileNoveau(result: List<ProfileItem>) {
        binding.profile.removeAllViews()
        result.forEach { profileItem ->
            val description = getProfileItemDescription(profileItem)
            addProfileItem(description, profileItem.value)
        }
    }

    private fun getProfileItemDescription(profileItem: ProfileItem): String {
        val descriptionStringId = when (profileItem.kind) {
            ProfileItem.Kind.Status -> R.string.profile_item_status
            ProfileItem.Kind.CustomerId -> R.string.profile_item_customer_id
            ProfileItem.Kind.Owner -> R.string.profile_item_owner
            ProfileItem.Kind.PhoneNumber -> R.string.profile_item_phone_number
            ProfileItem.Kind.EmailAddress -> R.string.profile_item_email_address
            ProfileItem.Kind.Unknown -> null
        }
        return if (descriptionStringId != null) {
            getString(descriptionStringId)
        } else {
            profileItem.description
        }
    }

    private fun addProfileItem(description: String, value: String) {
        val profileItem = layoutInflater.inflate(R.layout.profile_item, binding.consumptions, false)
        profileItem.findViewById<TextView>(R.id.txtLabel).text = description
        profileItem.findViewById<TextView>(R.id.txtValue).text = value
        binding.profile.addView(profileItem)
    }

    private fun openPlayStore() {
        requireContext().openPlayStore()
    }

}
