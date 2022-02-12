package de.lorenzgorse.coopmobile.backend

import de.lorenzgorse.coopmobile.coopclient.CoopClient
import de.lorenzgorse.coopmobile.coopclient.CoopLogin
import de.lorenzgorse.coopmobile.coopclient.RealCoopClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface CoopClientFactory {
    suspend fun get(): CoopClient?
    suspend fun refresh(invalidateSession: Boolean = false): CoopClient?
    fun clear()
}

interface CredentialsStore {
    fun loadCredentials(): Pair<String, String>?
    fun setSession(sessionId: String)
    fun loadSession(): String?
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
        return RealCoopClient(sessionId).also { instance = it }
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
