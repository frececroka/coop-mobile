package de.lorenzgorse.coopmobile.ui.consumption

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.formatter.ValueFormatter
import de.lorenzgorse.coopmobile.*
import de.lorenzgorse.coopmobile.databinding.FragmentConsumptionLogBinding
import de.lorenzgorse.coopmobile.ui.RemoteDataView
import de.lorenzgorse.coopmobile.ui.applyVisibility
import de.lorenzgorse.coopmobile.ui.onEach
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@ExperimentalCoroutinesApi
@FlowPreview
class ConsumptionFragment : Fragment() {

    @Inject lateinit var viewModel: ConsumptionData
    @Inject lateinit var consumptionLogCache: ConsumptionLogCache
    @Inject lateinit var analytics: FirebaseAnalytics

    private lateinit var binding: FragmentConsumptionLogBinding
    private lateinit var remoteDataView: RemoteDataView

    object Ranges {
        val range1w = Duration.ofDays(7)!!
        val range1m = Duration.ofDays(30)!!
        val range3m = Duration.ofDays(91)!!
        val range6m = Duration.ofDays(182)!!
        val range1y = Duration.ofDays(365)!!
        val rangemax = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coopComponent().inject(this)

        // Initialize range to 1 month.
        lifecycleScope.launch {
            viewModel.range.emit(Ranges.range1m)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        remoteDataView = RemoteDataView.inflate(inflater, container, R.layout.fragment_consumption_log)
        binding = FragmentConsumptionLogBinding.bind(remoteDataView.contentView)
        return remoteDataView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        remoteDataView.bindState(viewModel.state)
    }

    override fun onStart() {
        super.onStart()
        analytics.setScreen("Consumption")
        prepareChart()

        viewLifecycleOwner.onEach(viewModel.state.data()) {
            binding.consumptionChart.data = it
            binding.consumptionChart.invalidate()
        }

        val hasDataFlow = viewModel.visibleConsumptionLog.data().filterNotNull()
            .map { it.size >= 2 }
        viewLifecycleOwner.applyVisibility(hasDataFlow, binding.consumptionChart)
        viewLifecycleOwner.applyVisibility(hasDataFlow.map { !it }, binding.noConsumptionChart)

        val rangeButtons = listOf(
            Pair(binding.bt1w, Ranges.range1w),
            Pair(binding.bt1m, Ranges.range1m),
            Pair(binding.bt3m, Ranges.range3m),
            Pair(binding.bt6m, Ranges.range6m),
            Pair(binding.bt1y, Ranges.range1y),
            Pair(binding.btmax, Ranges.rangemax),
        )

        // Forward button clicks to view model.
        rangeButtons.forEach {
            val (bt, range) = it
            viewLifecycleOwner.onEach(bt.onClickFlow()) {
                viewModel.range.emit(range)
            }
        }

        // Forward view model updates to buttons.
        viewLifecycleOwner.onEach(viewModel.range) { range ->
            binding.rangeButtonsGroup.clearChecked()
            val (button, _) = rangeButtons.find { range == it.second } ?: return@onEach
            binding.rangeButtonsGroup.check(button.id)
        }

        // Enable buttons that are useful. If the oldest data point is 4 months
        // old, enable buttons up to 6 months, but not 1 year and max.
        viewLifecycleOwner.onEach(viewModel.consumptionLog.data().filterNotNull()) { entries ->
            for ((button, _) in rangeButtons) {
                button.isEnabled = false
            }
            val first = entries.minOfOrNull { it.instant }
                ?: return@onEach
            val now = Instant.now()
            for ((button, range) in rangeButtons) {
                button.isEnabled = true
                if (range != null && now.minus(range).isBefore(first)) {
                    // This button covers all data
                    break
                }
            }
        }
    }

    private fun prepareChart() {
        binding.consumptionChart.isDragEnabled = false
        binding.consumptionChart.isDoubleTapToZoomEnabled = false
        binding.consumptionChart.isScaleXEnabled = false
        binding.consumptionChart.isScaleYEnabled = false
        binding.consumptionChart.legend.isEnabled = false
        binding.consumptionChart.description.isEnabled = false
        binding.consumptionChart.axisRight.isEnabled = false

        val xAxis = binding.consumptionChart.xAxis
        xAxis.labelRotationAngle = 90f
        xAxis.valueFormatter = NewDateValueFormatter()
        xAxis.textColor = requireContext().getColor(R.color.colorOnBackground)

        val yAxis = binding.consumptionChart.axisLeft
        yAxis.axisMinimum = 0f
        yAxis.textColor = requireContext().getColor(R.color.colorOnBackground)
    }

}

class NewDateValueFormatter : ValueFormatter() {
    private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    override fun getFormattedValue(value: Float): String {
        val instant = Instant.ofEpochMilli(value.toLong())
        val localDate = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        return dateFormat.format(localDate)
    }
}
