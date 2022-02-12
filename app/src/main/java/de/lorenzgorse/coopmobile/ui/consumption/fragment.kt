package de.lorenzgorse.coopmobile.ui.consumption

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.components.ThemeUtils
import de.lorenzgorse.coopmobile.data.data
import de.lorenzgorse.coopmobile.setScreen
import de.lorenzgorse.coopmobile.ui.RemoteDataView
import kotlinx.android.synthetic.main.fragment_consumption_log.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@ExperimentalCoroutinesApi
@FlowPreview
class ConsumptionFragment : Fragment() {

    private lateinit var analytics: FirebaseAnalytics
    private lateinit var viewModel: ConsumptionData
    private lateinit var remoteDataView: RemoteDataView
    private lateinit var themeUtils: ThemeUtils
    private lateinit var consumptionLogCache: ConsumptionLogCache

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics = FirebaseAnalytics.getInstance(requireContext())
        themeUtils = ThemeUtils(requireContext())
        viewModel = ViewModelProvider(this).get(ConsumptionData::class.java)
        consumptionLogCache = ConsumptionLogCache(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        remoteDataView = RemoteDataView.inflate(inflater, container, R.layout.fragment_consumption_log)
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
        lifecycleScope.launch {
            viewModel.state.data().collect {
                consumptionChart.data = it
                consumptionChart.invalidate()
            }
        }
    }

    private fun prepareChart() {
        consumptionChart.isScaleXEnabled = true
        consumptionChart.isScaleYEnabled = false
        consumptionChart.legend.isEnabled = false
        consumptionChart.description.isEnabled = false
        consumptionChart.axisRight.isEnabled = false

        val xAxis = consumptionChart.xAxis
        xAxis.labelRotationAngle = 30f
        xAxis.valueFormatter = NewDateValueFormatter()
        xAxis.textColor = themeUtils.textColor()

        val yAxis = consumptionChart.axisLeft
        yAxis.axisMinimum = 0f
        yAxis.textColor = themeUtils.textColor()
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
