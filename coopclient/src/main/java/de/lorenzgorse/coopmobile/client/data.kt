package de.lorenzgorse.coopmobile.client

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.net.URL
import java.time.Instant

data class LabelledAmounts(
    val kind: Kind,
    val description: String,
    val labelledAmounts: List<LabelledAmount>
) {
    enum class Kind {
        Unknown,
        Credit,
        DataSwitzerland,
        DataEurope,
        DataSwitzerlandAndEurope,
        CallsAndSmsSwitzerland,
        OptionsAndCalls;

        companion object {
            private val toString: Map<Kind, List<String>> = mapOf(
                Credit to listOf(
                    "Mein verfügbarer Kredit",
                    "Il mio credito disponibile",
                    "Mon crédit disponible",
                ),
                DataSwitzerland to listOf(
                    "Mobile Daten in der Schweiz",
                    "Dati mobili in Svizzera",
                    "Données mobiles en Suisse",
                    "Daten in der Schweiz",
                    "Dati in Svizzera",
                    "Données en Suisse",
                ),
                DataEurope to listOf(
                    "Daten in EU/Westeuropa",
                    "Dati mobili nell'UE/Europa occidentale",
                    "TODO: fr",
                ),
                DataSwitzerlandAndEurope to listOf(
                    "Daten in der Schweiz inkl. EU/Westeuropa",
                    "Dati in Svizzera, UE/Europa Occidentale inclusi",
                    "Données en Suisse, UE et Europe de l`Ouest incl.",
                ),
                CallsAndSmsSwitzerland to listOf(
                    "Mobil-Einheiten in der Schweiz",
                    "Unità mobili in Svizzera",
                    "Unités mobiles en Suisse",
                ),
                OptionsAndCalls to listOf(
                    "Optionen und inkludierte Anrufe",
                    "Opzioni e chiamate comprese",
                    "Options et appels inclus",
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

data class Amount(val value: Double, val unit: String?)

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
    val instant: Instant,
    val subject: String,
    val details: URL
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
