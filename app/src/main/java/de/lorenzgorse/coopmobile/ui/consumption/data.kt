package de.lorenzgorse.coopmobile.ui.consumption

import android.app.Application
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import de.lorenzgorse.coopmobile.*
import de.lorenzgorse.coopmobile.client.ConsumptionLogEntry
import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.UnitValueBlock
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.components.ThemeUtils
import de.lorenzgorse.coopmobile.data.CoopViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@FlowPreview
@ExperimentalCoroutinesApi
class ConsumptionData @Inject constructor(
    app: Application,
    client: CoopClient
) : CoopViewModel(app) {

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
        consumption: List<UnitValueBlock>,
        consumptionLog: List<ConsumptionLogEntry>
    ): LineData? {
        val currentMobileDataBlock = consumption.firstOrNull {
            it.kind == UnitValueBlock.Kind.DataSwitzerland
        } ?: return null

        val currentMobileData = currentMobileDataBlock.unitValues.firstOrNull()
            ?: return null

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
