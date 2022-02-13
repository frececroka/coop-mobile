package de.lorenzgorse.coopmobile

import de.lorenzgorse.coopmobile.client.simple.CoopLogin
import java.util.concurrent.atomic.AtomicReference

class MonitoredCoopLogin(private val userProperties: UserProperties, private val coopLogin: CoopLogin) : CoopLogin {
    override suspend fun login(
        username: String,
        password: String,
        plan: AtomicReference<String?>?
    ): String? {
        val plan = plan ?: AtomicReference()
        val sessionId = coopLogin.login(username, password, plan) ?: return null
        plan.get()?.let { userProperties.setPlan(it) }
        return sessionId
    }
}
