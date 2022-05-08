package de.lorenzgorse.coopmobile.ui.consumption

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.formatter.ValueFormatter
import de.lorenzgorse.coopmobile.FirebaseAnalytics
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.components.ThemeUtils
import de.lorenzgorse.coopmobile.coopComponent
import de.lorenzgorse.coopmobile.data
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
import javax.inject.Inject

@ExperimentalCoroutinesApi
@FlowPreview
class ConsumptionFragment : Fragment() {

    @Inject lateinit var viewModel: ConsumptionData
    @Inject lateinit var consumptionLogCache: ConsumptionLogCache
    @Inject lateinit var themeUtils: ThemeUtils
    @Inject lateinit var analytics: FirebaseAnalytics

    private lateinit var remoteDataView: RemoteDataView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coopComponent().inject(this)
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
