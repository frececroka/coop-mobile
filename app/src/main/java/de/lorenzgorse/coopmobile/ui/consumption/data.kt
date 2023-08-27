package de.lorenzgorse.coopmobile.ui.consumption

import android.app.Application
import androidx.core.os.bundleOf
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import de.lorenzgorse.coopmobile.*
import de.lorenzgorse.coopmobile.client.Amount
import de.lorenzgorse.coopmobile.client.AmountUnit
import de.lorenzgorse.coopmobile.client.ConsumptionLogEntry
import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.LabelledAmounts
import de.lorenzgorse.coopmobile.client.simple.CoopClient
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
    private val app: Application,
    client: CoopClient,
    private val analytics: FirebaseAnalytics,
) : CoopViewModel(app) {

    private val consumptionLogCache = ConsumptionLogCache(app)

    val range: MutableSharedFlow<TemporalAmount?> = MutableSharedFlow(replay = 1)
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

        val mobileConsumptionLog = cachedConsumptionLog.mapValue { consumptionLog ->
            consumptionLog.filter {
                setOf(
                    "Daten in der Schweiz",
                    "Données en Suisse",
                    "Traffico dati in Svizzera"
                ).contains(it.type)
            }
        }

        val fallbackConsumptionLog = mobileConsumptionLog.mapValue {
            if (it.size < 10) fakeData() else it
        }

        val useFallbackConsumptionLog =
            Firebase.remoteConfig.getBoolean("use_fallback_consumption_log")
        consumptionLog =
            if (useFallbackConsumptionLog) fallbackConsumptionLog
            else mobileConsumptionLog

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

        analytics.logEvent("ConsumptionLog", bundleOf("Length" to consumptionLog.size))

        var currentData = try {
            amountToMb(currentMobileData.amount)
        } catch (e: IllegalArgumentException) {
            Firebase.crashlytics.recordException(e)
            return null
        }

        val chartData = consumptionLog
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
        dataSet.setCircleColor(app.getColor(R.color.colorAccent))
        dataSet.color = app.getColor(R.color.colorPrimary)
        dataSet.fillColor = app.getColor(R.color.colorPrimary)
        dataSet.fillAlpha = 30

        return LineData(dataSet)
    }

    private fun amountToMb(amount: Amount): Double {
        val unit = amount.unit ?: throw IllegalArgumentException("Missing unit: $amount")
        return when (unit.kind) {
            AmountUnit.Kind.MB -> amount.value
            AmountUnit.Kind.GB -> {
                // TODO: should this be 1024 or 1000?
                amount.value * 1024
            }
            else -> throw IllegalArgumentException("Unsupported unit: $amount")
        }
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
