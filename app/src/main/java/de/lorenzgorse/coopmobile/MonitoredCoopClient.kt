package de.lorenzgorse.coopmobile

import androidx.core.os.bundleOf
import arrow.core.Either
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

    override suspend fun getConsumption(): Either<CoopError, List<LabelledAmounts>> =
        super.getConsumption().also {
            if (it is Either.Right) logConsumptionBlocks(it.value)
        }

    private fun logConsumptionBlocks(consumptionBlocks: List<LabelledAmounts>) {
        for (consumptionBlock in consumptionBlocks) {
            Firebase.analytics.logEvent(
                "ConsumptionBlock",
                bundleOf(
                    "Kind" to consumptionBlock.kind,
                    "Description" to consumptionBlock.description,
                )
            )
            logConsumptions(consumptionBlock.labelledAmounts)
        }
    }

    private fun logConsumptions(consumptions: List<LabelledAmount>) {
        for (consumption in consumptions) {
            logConsumption(consumption)
        }
    }

    private fun logConsumption(consumptionItem: LabelledAmount) {
        Firebase.analytics.logEvent(
            "ConsumptionItem",
            bundleOf(
                "Description" to consumptionItem.description,
                "Unit" to consumptionItem.amount.unit,
            )
        )
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
                is CoopError.PlanUnsupported,
                is CoopError.HtmlChanged,
                is CoopError.BadHtml,
                is CoopError.Unauthorized ->
                    Firebase.crashlytics.recordException(result.value)
                is CoopError.FailedLogin,
                is CoopError.NoClient,
                is CoopError.NoNetwork,
                is CoopError.Other -> {}
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

}
