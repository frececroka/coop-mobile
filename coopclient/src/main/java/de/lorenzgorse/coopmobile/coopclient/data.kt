package de.lorenzgorse.coopmobile.coopclient

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.net.URL
import java.util.*

data class UnitValue<T>(
    val description: String,
    val amount: T,
    val unit: String
)

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
    val date: Date,
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
    val date: Date,
    val type: String,
    val amount: Double
)
