package de.lorenzgorse.coopmobile.ui.consumption

import android.app.Application
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.backend.CoopError
import de.lorenzgorse.coopmobile.components.ThemeUtils
import de.lorenzgorse.coopmobile.coopclient.ConsumptionLogEntry
import de.lorenzgorse.coopmobile.coopclient.UnitValue
import de.lorenzgorse.coopmobile.createClient
import de.lorenzgorse.coopmobile.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@FlowPreview
@ExperimentalCoroutinesApi
class ConsumptionData(app: Application) : CoopViewModel(app) {

    private val client = createClient(app)
    private val themeUtils: ThemeUtils = ThemeUtils(app)
    private val consumptionLogCache = ConsumptionLogCache(app)

    val state: Flow<State<LineData?, CoopError>>

    init {
        val consumptionLog = load { client.getConsumptionLog() }
            .flatMap { v, n ->
                if (v != null) State.Loaded(v, n)
                else {
                    val message = app.getString(R.string.consumption_unavailable)
                    State.Errored(CoopError.Other(message), n)
                }
            }

        val cachedConsumptionLog = consumptionLog.mapValue {
            consumptionLogCache.insert(it)
            consumptionLogCache.load()
        }

        val consumption = load { client.getConsumption() }

        state = liftFlow(consumption, cachedConsumptionLog) { c, cl ->
            makeLineData(c, cl)
        }.share()
    }

    private fun makeLineData(
        consumption: List<UnitValue<Float>>,
        consumptionLog: List<ConsumptionLogEntry>
    ): LineData? {
        val currentMobileData = consumption.firstOrNull {
            setOf(
                "Mobile Daten in der Schweiz",
                "Données mobiles en Suisse",
                "Dati mobili in Svizzera"
            ).contains(it.description)
        } ?: return null

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

        return LineData(dataSet)
    }

}
