package de.lorenzgorse.coopmobile

import android.content.Context
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import de.lorenzgorse.coopmobile.components.KV
import org.slf4j.LoggerFactory

class UserProperties(context: Context) {

    data class Data(val plan: String?)

    private val log = LoggerFactory.getLogger(javaClass)

    fun restore() {
        apply(data())
    }

    fun setPlan(plan: String) = mutate { it.copy(plan = plan) }

    private fun mutate(op: (Data) -> Data) {
        val old = data()
        val new = op(old)
        log.info("Changed user properties from $old to $new")
        apply(new)
        setData(new)
    }

    private fun apply(data: Data) {
        log.info("Applying user properties: $data")
        Firebase.analytics.setUserProperty("Plan", data.plan)
    }

    private val kv = KV(context)
    private fun data() = kv.get<Data>("UserProperties", Data::class.java) ?: Data(null)
    private fun setData(data: Data) = kv.set("UserProperties", data)

}
