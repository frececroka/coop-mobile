package de.lorenzgorse.coopmobile.ui.consumption

import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.*
import de.lorenzgorse.coopmobile.components.ThemeUtils
import de.lorenzgorse.coopmobile.coopclient.ConsumptionLogEntry
import de.lorenzgorse.coopmobile.coopclient.UnitValue
import de.lorenzgorse.coopmobile.data.ApiDataViewModel
import de.lorenzgorse.coopmobile.data.Value
import de.lorenzgorse.coopmobile.ui.handleLoadDataError
import kotlinx.android.synthetic.main.fragment_consumption_log.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

class ConsumptionFragment : Fragment() {

    private lateinit var analytics: FirebaseAnalytics
    private lateinit var themeUtils: ThemeUtils
    private lateinit var viewModel: ConsumptionViewModel
    private lateinit var consumptionLogCache: ConsumptionLogCache

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics = FirebaseAnalytics.getInstance(requireContext())
        themeUtils = ThemeUtils(requireContext())
        viewModel = ViewModelProvider(this).get(ConsumptionViewModel::class.java)
        consumptionLogCache = ConsumptionLogCache(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_consumption_log, container, false)
    }

    override fun onStart() {
        super.onStart()
        analytics.setScreen("Consumption")
        prepareChart()
        viewModel.data.removeObservers(this)
        viewModel.data.observe(this, Observer(::setData))
    }

    private fun setData(result: Value<Pair<List<UnitValue<Float>>, List<ConsumptionLogEntry>?>>) {
        when (result) {
            is Value.Initiated -> { }
            is Value.Failure ->
                handleLoadDataError(result.error)
            is Value.Success -> lifecycleScope.launch {
                onSuccess(result.value.first, result.value.second)
            }
        }
    }

    private suspend fun onSuccess(data: List<UnitValue<Float>>, consumptionLog: List<ConsumptionLogEntry>?) {
        if (consumptionLog != null) {
            consumptionLogCache.insert(consumptionLog)
            updateChart(data, consumptionLogCache.load())
            loading.visibility = View.GONE
            consumptionChart.visibility = View.VISIBLE
        } else {
            notify(getString(R.string.consumption_unavailable))
            findNavController().navigate(R.id.action_overview)
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
        xAxis.valueFormatter = makeDateValueFormatter()
        xAxis.textColor = themeUtils.textColor()

        val yAxis = consumptionChart.axisLeft
        yAxis.axisMinimum = 0f
        yAxis.textColor = themeUtils.textColor()
    }

    private fun updateChart(data: List<UnitValue<Float>>, consumptionLog: List<ConsumptionLogEntry>) {
        val currentMobileData = data.firstOrNull {
            setOf(
                "Mobile Daten in der Schweiz",
                "Données mobiles en Suisse",
                "Dati mobili in Svizzera"
            ).contains(it.description)
        } ?: return

        val mobileDataConsumption = consumptionLog.filter {
            setOf(
                "Daten in der Schweiz",
                "Données en Suisse",
                "Traffico dati in Svizzera"
            ).contains(it.type)
        }

        var currentData = currentMobileData.amount.toDouble()
        val chartData = mobileDataConsumption
            .sortedBy { it.date }
            .reversed()
            .map { entry ->
                val date = entry.date.time
                currentData += entry.amount / 1024
                Entry(date.toFloat(), currentData.toFloat())
            }
            .reversed()

        val dataSet = LineDataSet(chartData, "Mobile Data")
        dataSet.setDrawCircles(false)
        dataSet.lineWidth = 3f
        dataSet.color = themeUtils.getColor(R.attr.colorAccent)
        dataSet.fillColor = themeUtils.getColor(R.attr.colorPrimary)
        dataSet.fillAlpha = 30
        dataSet.setDrawFilled(true)

        consumptionChart.data = LineData(dataSet)
        consumptionChart.invalidate()
    }

}

fun makeDateValueFormatter(): ValueFormatter =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        NewDateValueFormatter()
    else
        LegacyDateValueFormatter()

@RequiresApi(Build.VERSION_CODES.O)
class NewDateValueFormatter : ValueFormatter() {
    private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    override fun getFormattedValue(value: Float): String {
        val instant = Instant.ofEpochMilli(value.toLong())
        val localDate = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        return dateFormat.format(localDate)
    }
}

class LegacyDateValueFormatter : ValueFormatter() {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    override fun getFormattedValue(value: Float): String =
        dateFormat.format(Date(value.toLong()))
}

class ConsumptionViewModel(
    app: Application
): ApiDataViewModel<Pair<List<UnitValue<Float>>, List<ConsumptionLogEntry>?>>(app, { {
    Pair(it.getConsumption(), it.getConsumptionLog())
} })
