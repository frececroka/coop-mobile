package de.lorenzgorse.coopmobile

import androidx.core.os.bundleOf
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.perf.ktx.performance
import de.lorenzgorse.coopmobile.client.*
import de.lorenzgorse.coopmobile.client.simple.CoopClient

class MonitoredCoopClient(private val client: CoopClient) : DecoratedCoopClient() {

    override suspend fun getProfile(): Either<CoopError, List<Pair<String, String>>> {
        val profile = super.getProfile()
        if (profile is Either.Right) {
            Firebase.analytics.logEvent("ProfileItems", bundleOf("Length" to profile.value.size))
            for (profileItem in profile.value) {
                Firebase.analytics.logEvent(
                    "ProfileItem",
                    bundleOf("Description" to profileItem.first)
                )
            }
        }
        return profile
    }

    override suspend fun getConsumption(): Either<CoopError, List<UnitValue<Float>>> =
        super.getConsumption().also { logConsumption(it) }

    override suspend fun getConsumptionGeneric(): Either<CoopError, List<UnitValue<Float>>> =
        super.getConsumptionGeneric().also { logConsumption(it) }

    private fun logConsumption(consumption: Either<CoopError, List<UnitValue<Float>>>) {
        if (consumption !is Either.Right) return
        Firebase.analytics.logEvent(
            "ConsumptionItems",
            bundleOf("Length" to consumption.value.size)
        )
        for (consumptionItem in consumption.value) {
            Firebase.analytics.logEvent(
                "ConsumptionItem",
                bundleOf(
                    "Description" to consumptionItem.description,
                    "Unit" to consumptionItem.unit,
                )
            )
        }
    }

    override suspend fun <T> decorator(
        loader: suspend (client: CoopClient) -> Either<CoopError, T>,
        method: String?
    ): Either<CoopError, T> {
        val trace = Firebase.performance.newTrace("LoadData")
        trace.start()

        if (method != null) {
            trace.putAttribute("Method", method)
        }

        val result = loader(client)

        val statusStr = when (result) {
            is Either.Left -> result.value::class.simpleName!!
            is Either.Right -> "Success"
        }

        trace.putAttribute("Status", statusStr)
        trace.stop()

        when (result) {
            is Either.Left -> when (result.value) {
                is CoopError.BadHtml, CoopError.Unauthorized ->
                    Firebase.crashlytics.recordException(result.value)
                else -> {}
            }
            else -> {}
        }

        Firebase.analytics.logEvent(
            "LoadData", bundleOf(
                "Method" to method,
                "Status" to statusStr,
            )
        )

        return result
    }

    override suspend fun sessionId(): String? = client.sessionId()

}
