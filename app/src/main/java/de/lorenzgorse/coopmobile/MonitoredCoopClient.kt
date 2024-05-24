package de.lorenzgorse.coopmobile

import androidx.core.os.bundleOf
import arrow.core.Either
import bifrost.Meter
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.perf.ktx.performance
import de.lorenzgorse.coopmobile.client.*
import de.lorenzgorse.coopmobile.client.simple.CoopClient

class MonitoredCoopClient(
    private val client: CoopClient,
    private val meter: Meter,
) : DecoratedCoopClient() {

    override suspend fun getProfile(): Either<CoopError, List<ProfileItem>> {
        val profile = super.getProfile()
        if (profile is Either.Right) {
            Firebase.analytics.logEvent("ProfileItems", bundleOf("Length" to profile.value.size))
            for (profileItem in profile.value) {
                Firebase.analytics.logEvent(
                    "ProfileItem",
                    bundleOf(
                        "Kind" to profileItem.kind.name,
                        "Description" to profileItem.description,
                    )
                )
                if (profileItem.kind == ProfileItem.Kind.Unknown) {
                    val msg = "No mapped ProfileItem kind for description '${profileItem.description}'"
                    Firebase.crashlytics.recordException(IllegalArgumentException(msg))
                }
            }
        }
        return profile
    }

    override suspend fun getConsumption(): Either<CoopError, List<LabelledAmounts>> =
        super.getConsumption().also {
            if (it is Either.Right) logConsumptionBlocks(it.value)
        }

    private fun logConsumptionBlocks(consumptionBlocks: List<LabelledAmounts>) {
        meter.increment("coopmobile_coopclient_consumptionblocks_length", mapOf("length" to consumptionBlocks.size.toString()))
        for (consumptionBlock in consumptionBlocks) {
            Firebase.analytics.logEvent(
                "ConsumptionBlock",
                bundleOf(
                    "Kind" to consumptionBlock.kind.name,
                    "Description" to consumptionBlock.description,
                )
            )
            logConsumptions(consumptionBlock.labelledAmounts)
            for (subtitle in consumptionBlock.subtitles) {
                Firebase.analytics.logEvent(
                    "ConsumptionBlockSubtitle",
                    bundleOf("Description" to subtitle)
                )
            }
            val wellFormedError = isWellFormed(consumptionBlock)
            if (wellFormedError != null) {
                val message = "LabelledAmounts is not well formed:\n$consumptionBlock"
                Firebase.crashlytics.recordException(IllegalArgumentException(message, wellFormedError))
                // TODO: return HtmlChanged?
            }
        }
    }

    private fun isWellFormed(labelledAmounts: LabelledAmounts): IllegalArgumentException? {
        if (labelledAmounts.kind == LabelledAmounts.Kind.Unknown) {
            return IllegalArgumentException("No mapped LabelledAmounts.Kind")
        }
        val labelledAmount = labelledAmounts.labelledAmounts.firstOrNull()
            ?: return IllegalArgumentException("No LabelledAmounts")
        if (labelledAmount.description != "verbleibend") {
            return IllegalArgumentException("First LabelledAmount is not the remaining quota")
        }
        if (labelledAmount.amount.value.isFinite() && labelledAmount.amount.unit == null) {
            return IllegalArgumentException("First LabelledAmount is finite but has no unit")
        }
        return null
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
                "Unit" to consumptionItem.amount.unit?.kind?.name,
            )
        )
        val unit = consumptionItem.amount.unit
        if (unit?.kind == AmountUnit.Kind.Unknown) {
            val msg = "No mapped AmountUnit for text '${unit.source}'"
            Firebase.crashlytics.recordException(IllegalArgumentException(msg))
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
        meter.increment(
            "coopmobile_coopclient_loaddata",
            mapOf(
                "Method" to (method ?: "unknown"),
                "Status" to statusStr,
            )
        )

        return result
    }

}
