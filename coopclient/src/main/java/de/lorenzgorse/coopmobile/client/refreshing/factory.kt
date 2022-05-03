package de.lorenzgorse.coopmobile.client.refreshing

import de.lorenzgorse.coopmobile.client.Config
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.client.simple.CoopLogin
import de.lorenzgorse.coopmobile.client.simple.HttpClientFactory
import de.lorenzgorse.coopmobile.client.simple.StaticSessionCoopClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface CoopClientFactory {
    suspend fun get(): CoopClient?
    suspend fun refresh(oldClient: CoopClient? = null): CoopClient?
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
    private val staticSessionCoopClient: (String) -> CoopClient,
) : CoopClientFactory {

    private var instance: CoopClient? = null
    private val mtx = Mutex()

    override suspend fun get(): CoopClient? = mtx.withLock {
        if (instance == null) {
            refreshInternal()
        }
        return instance
    }

    override suspend fun refresh(oldClient: CoopClient?): CoopClient? = mtx.withLock {
        // Mutexes are not reentrant, so we need a public version of the method that aquires the mutex
        // and an internal version that doesn't.
        refreshInternal(oldClient)
    }

    private suspend fun refreshInternal(oldClient: CoopClient? = null): CoopClient? {
        val invalidateSession = oldClient != null && instance == oldClient
        val sessionId = newSession(invalidateSession) ?: return null
        return staticSessionCoopClient(sessionId).also { instance = it }
    }

    private suspend fun newSession(invalidateSession: Boolean): String? {
        val sessionId = if (invalidateSession) {
            newSessionFromSavedCredentials()
        } else {
            credentialsStore.loadSession() ?: newSessionFromSavedCredentials()
        }
        if (sessionId != null) {
            credentialsStore.setSession(sessionId)
        }
        return sessionId
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun newSessionFromSavedCredentials(): String? {
        val (username, password) = credentialsStore.loadCredentials() ?: return null
        return coopLogin.login(username, password, CoopLogin.Origin.SessionRefresh)
    }

    override fun clear() {
        instance = null
    }

}
