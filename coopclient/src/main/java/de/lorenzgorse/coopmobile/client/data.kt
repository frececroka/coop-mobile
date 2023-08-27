package de.lorenzgorse.coopmobile.client

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.net.URL
import java.time.Instant
import java.time.LocalDate

data class LabelledAmounts(
    val kind: Kind,
    val description: String,
    val labelledAmounts: List<LabelledAmount>,
    // TODO: remove this field after it's not useful anymore
    val subtitles: List<String> = listOf(),
) {
    constructor(description: String, labelledAmounts: List<LabelledAmount>, subtitles: List<String>)
            : this(Kind.fromString(description), description, labelledAmounts, subtitles)

    enum class Kind {
        Unknown,
        Credit,
        DataSwitzerland,
        DataEurope,
        DataSwitzerlandAndEurope,
        CallsAndSmsSwitzerland,

        // Options and included calls
        // Use this query:
        //     select
        //       event_params.description,
        //       count(*) as count
        //     from `coop-mobile-df71e.analytics_200391596.processed_events`
        //     where event_name = "ConsumptionBlockSubtitle" and date >= "20230825"
        //     group by event_params.description;
        MessageTravel,
        VoiceTravelEurope,
        VoiceTravelWorld1,
        VoiceTravelWorld2,
        DataTravelEurope,
        DataTravelWorld1,
        DataTravelWorld2,

        // TODO: remove this option if it's not used anymore
        OptionsAndCalls;

        // Use this query to find these values:
        //     select
        //       event_params.kind,
        //       event_params.description,
        //       count(*) as count,
        //       min(event_time) as first_seen,
        //       max(event_time) as last_seen,
        //       min(app_info.version) as first_app_version,
        //       max(app_info.version) as last_app_version,
        //     from `coop-mobile-df71e.analytics_200391596.processed_events`
        //     where
        //       event_name = "ConsumptionBlock"
        //       and app_info.version >= "2.0000101"
        //       and date >= "20230601"
        //     group by
        //       event_params.kind,
        //       event_params.description
        //     order by count desc;
        companion object {
            private val toString: Map<Kind, List<String>> = mapOf(
                Credit to listOf(
                    "Mein verfügbarer Kredit",
                ),
                DataSwitzerland to listOf(
                    "Mobile Daten in der Schweiz",
                    "Daten in der Schweiz",
                ),
                DataEurope to listOf(
                    "Daten in EU/Westeuropa",
                ),
                DataSwitzerlandAndEurope to listOf(
                    "Daten in der Schweiz inkl. EU/Westeuropa",
                ),
                CallsAndSmsSwitzerland to listOf(
                    "Mobil-Einheiten in der Schweiz",
                ),
                MessageTravel to listOf("Message Travel"),
                VoiceTravelEurope to listOf("Voice Travel - EU/Westeuropa"),
                VoiceTravelWorld1 to listOf("Voice Travel - Welt 1"),
                VoiceTravelWorld2 to listOf("Voice Travel - Welt 2"),
                DataTravelEurope to listOf("Data Travel - EU/Westeuropa"),
                DataTravelWorld1 to listOf("Data Travel - Welt 1"),
                DataTravelWorld2 to listOf("Data Travel - Welt 2"),
                OptionsAndCalls to listOf(
                    "Optionen und inkludierte Anrufe",
                ),
            )

            private val fromString: Map<String, Kind> = buildMap {
                for ((kind, strings) in toString.entries) {
                    for (string in strings) {
                        put(string, kind)
                    }
                }
            }

            fun fromString(text: String): Kind = fromString[text] ?: Unknown
        }
    }
}

data class LabelledAmount(
    val description: String,
    val amount: Amount,
)

data class Amount(val value: Double, val unit: AmountUnit?)

data class AmountUnit(val kind: Kind, val source: String) {
    constructor(source: String) : this(Kind.fromString(source), source)

    enum class Kind {
        CHF,
        Units,
        Minutes,
        MB,
        GB,
        Unlimited,
        Unknown;

        companion object {
            fun fromString(unit: String) = when (unit) {
                "CHF" -> CHF
                "Einheiten" -> Units
                "Min" -> Minutes
                "MB" -> MB
                "GB" -> GB
                "unbegrenzt" -> Unlimited
                else -> Unknown
            }
        }
    }
}

data class ProfileItem(val kind: Kind, val description: String, val value: String) {
    constructor(description: String, value: String)
            : this(Kind.fromString[description] ?: Kind.Unknown, description, value)

    enum class Kind {
        Status,
        CustomerId,
        Owner,
        PhoneNumber,
        EmailAddress,
        Unknown;

        companion object {
            val fromString = mapOf(
                "Status" to Status,
                "Kundennummer" to CustomerId,
                "Inhaber" to Owner,
                "Handynummer" to PhoneNumber,
                "E-Mail Adresse" to EmailAddress,
            )
        }
    }
}

data class Product(
    val name: String,
    val description: String,
    val price: String,
    val buySpec: ProductBuySpec
)

data class ProductBuySpec(
    val url: String,
    val parameters: Map<String, String>
) : Serializable

data class Correspondence(
    val header: CorrespondenceHeader,
    val message: String
)

data class CorrespondenceHeader(
    val date: LocalDate,
    val subject: String,
    val details: URL,
)

data class RawConsumptionLog(val data: List<RawConsumptionLogEntry>)
data class RawConsumptionLogEntry(
    @SerializedName("start_date") val date: String,
    @SerializedName("is_data") val isData: Boolean,
    val type: String,
    val amount: String
)

data class ConsumptionLogEntry(
    val instant: Instant,
    val type: String,
    val amount: Double
)
