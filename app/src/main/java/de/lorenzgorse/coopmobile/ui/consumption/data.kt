package de.lorenzgorse.coopmobile.ui.consumption

import android.app.Application
import androidx.core.os.bundleOf
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import de.lorenzgorse.coopmobile.*
import de.lorenzgorse.coopmobile.client.ConsumptionLogEntry
import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.LabelledAmounts
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.components.ThemeUtils
import de.lorenzgorse.coopmobile.data.CoopViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.time.Duration
import java.time.Instant
import java.time.temporal.TemporalAmount
import javax.inject.Inject
import kotlin.random.Random

@FlowPreview
@ExperimentalCoroutinesApi
class ConsumptionData @Inject constructor(
    app: Application,
    client: CoopClient,
    private val analytics: FirebaseAnalytics,
) : CoopViewModel(app) {

    private val themeUtils: ThemeUtils = ThemeUtils(app)
    private val consumptionLogCache = ConsumptionLogCache(app)

    val range: MutableSharedFlow<TemporalAmount?> = MutableSharedFlow()
    val consumptionLog: Flow<State<List<ConsumptionLogEntry>, CoopError>>
    val visibleConsumptionLog: Flow<State<List<ConsumptionLogEntry>, CoopError>>
    val state: Flow<State<LineData?, CoopError>>

    init {
        val newConsumptionLog = load { client.getConsumptionLog() }
            .flatMap { v, n ->
                if (v != null) State.Loaded(v, n)
                else {
                    val message = app.getString(R.string.consumption_unavailable)
                    State.Errored(CoopError.Other(message), n)
                }
            }

        val cachedConsumptionLog = newConsumptionLog.mapValue {
            consumptionLogCache.insert(it)
            consumptionLogCache.load()
        }

        val fallbackConsumptionLog = cachedConsumptionLog.mapValue {
            if (it.size < 10) fakeData() else it
        }

        val useFallbackConsumptionLog =
            Firebase.remoteConfig.getBoolean("use_fallback_consumption_log")
        consumptionLog =
            if (useFallbackConsumptionLog) fallbackConsumptionLog
            else cachedConsumptionLog

        visibleConsumptionLog = liftFlow(consumptionLog, range.toState()) { consumptionLog, range ->
            val begin = if (range != null)
                Instant.now().minus(range)
                else Instant.MIN
            consumptionLog.filter { it.instant.isAfter(begin) }
        }

        val consumption = load { client.getConsumption() }

        state = liftFlow(consumption, visibleConsumptionLog) { c, cl ->
            makeLineData(c, cl)
        }.share()
    }

    private fun makeLineData(
        consumption: List<LabelledAmounts>,
        consumptionLog: List<ConsumptionLogEntry>
    ): LineData? {
        val currentMobileDataBlock = consumption.firstOrNull {
            it.kind == LabelledAmounts.Kind.DataSwitzerland
        } ?: return null

        val currentMobileData = currentMobileDataBlock.labelledAmounts.firstOrNull()
            ?: return null

        val mobileDataConsumption = consumptionLog.filter {
            setOf(
                "Daten in der Schweiz",
                "Données en Suisse",
                "Traffico dati in Svizzera"
            ).contains(it.type)
        }

        analytics.logEvent("ConsumptionLog", bundleOf("Length" to mobileDataConsumption.size))

        var currentData = currentMobileData.amount.value
        val chartData = mobileDataConsumption
            .sortedBy { it.instant }
            .reversed()
            .map { entry ->
                val epochMillis = entry.instant.toEpochMilli()
                currentData += entry.amount / 1024
                Entry(epochMillis.toFloat(), currentData.toFloat())
            }
            .reversed()

        val dataSet = LineDataSet(chartData, "Mobile Data")
        dataSet.setDrawValues(false)
        dataSet.setDrawCircles(true)
        dataSet.setDrawFilled(true)
        dataSet.circleRadius = 2f
        dataSet.lineWidth = 3f
        dataSet.setCircleColor(themeUtils.getColor(R.attr.colorAccent))
        dataSet.color = themeUtils.getColor(R.attr.colorPrimary)
        dataSet.fillColor = themeUtils.getColor(R.attr.colorPrimary)
        dataSet.fillAlpha = 30

        return LineData(dataSet)
    }

    private fun fakeData(): List<ConsumptionLogEntry> {
        var instant = Instant.now()
        return (0 until 100).map {
            val dataDelta = Random.nextDouble() * 100 * 1024
            val timeDelta = Random.nextDouble() * 60 * 24 * 2
            instant = instant.minus(Duration.ofMinutes(timeDelta.toLong()))
            ConsumptionLogEntry(instant, "Daten in der Schweiz", dataDelta)
        }
    }

}
