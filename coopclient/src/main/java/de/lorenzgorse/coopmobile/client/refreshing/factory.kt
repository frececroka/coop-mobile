package de.lorenzgorse.coopmobile.client.refreshing

import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.client.simple.CoopLogin
import de.lorenzgorse.coopmobile.client.simple.StaticSessionCoopClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface CoopClientFactory {
    suspend fun get(): CoopClient?
    suspend fun refresh(invalidateSession: Boolean = false): CoopClient?
    fun clear()
}

interface CredentialsStore {
    fun setCredentials(username: String, password: String)
    fun loadCredentials(): Pair<String, String>?
    fun clearCredentials()
    fun setSession(sessionId: String)
    fun loadSession(): String?
    fun clearSession()
}

class RealCoopClientFactory(
    private val credentialsStore: CredentialsStore,
    private val coopLogin: CoopLogin,
) : CoopClientFactory {

    private var instance: CoopClient? = null

    private val refreshMtx = Mutex()
    override suspend fun get(): CoopClient? = refreshMtx.withLock {
        if (instance == null) {
            refresh()
        }
        return instance
    }

    override suspend fun refresh(invalidateSession: Boolean): CoopClient? {
        val sessionId = newSession(invalidateSession) ?: return null
        return StaticSessionCoopClient(sessionId).also { instance = it }
    }

    private val sessionMtx = Mutex()
    private suspend fun newSession(invalidateSession: Boolean): String? = sessionMtx.withLock {
        val sessionId = if (invalidateSession) {
            newSessionFromSavedCredentials()
        } else {
            credentialsStore.loadSession() ?: newSessionFromSavedCredentials()
        }
        if (sessionId != null) {
            credentialsStore.setSession(sessionId)
        }
        sessionId
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun newSessionFromSavedCredentials(): String? {
        val (username, password) = credentialsStore.loadCredentials() ?: return null
        return coopLogin.login(username, password)
    }

    override fun clear() {
        instance = null
    }

}
