package de.lorenzgorse.coopmobile.client.refreshing

import arrow.core.Either
import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.client.simple.CoopLogin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.IOException
import org.slf4j.LoggerFactory

interface CoopClientFactory {
    suspend fun get(): Either<CoopError, CoopClient>
    suspend fun refresh(oldClient: CoopClient? = null): Either<CoopError, CoopClient>
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

    private val log = LoggerFactory.getLogger(RealCoopClientFactory::class.java)

    private var instance: CoopClient? = null
    private val mtx = Mutex()

    override suspend fun get(): Either<CoopError, CoopClient> = mtx.withLock {
        return Either.Right(instance ?: return refreshInternal())
    }

    override suspend fun refresh(oldClient: CoopClient?): Either<CoopError, CoopClient> = mtx.withLock {
        // Mutexes are not reentrant, so we need a public version of the method that aquires the mutex
        // and an internal version that doesn't.
        refreshInternal(oldClient)
    }

    private suspend fun refreshInternal(oldClient: CoopClient? = null): Either<CoopError, CoopClient> {
        val invalidateSession = oldClient != null && instance == oldClient
        log.info("Creating new client instance with invalidateSession=$invalidateSession")
        val sessionId = when (val sessionId = newSession(invalidateSession)) {
            is Either.Left -> return sessionId
            is Either.Right -> sessionId.value
        }
        val coopClient = staticSessionCoopClient(sessionId)
        instance = coopClient
        return Either.Right(coopClient)
    }

    private suspend fun newSession(invalidateSession: Boolean): Either<CoopError, String> {
        val currentSessionId = when {
            invalidateSession -> null
            else -> credentialsStore.loadSession()
        }
        val sessionId = currentSessionId
            ?: when (val sessionId = newSessionFromSavedCredentials()) {
                is Either.Left -> return sessionId
                is Either.Right -> sessionId.value
            }
        credentialsStore.setSession(sessionId)
        return Either.Right(sessionId)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun newSessionFromSavedCredentials(): Either<CoopError, String> {
        val (username, password) = credentialsStore.loadCredentials()
            ?: return Either.Left(CoopError.NoClient)
        return try {
            val sessionId = coopLogin.login(username, password, CoopLogin.Origin.SessionRefresh)
                ?: return Either.Left(CoopError.FailedLogin)
            Either.Right(sessionId)
        } catch (_: IOException) {
            Either.Left(CoopError.NoNetwork)
        }
    }

    override fun clear() {
        instance = null
    }

}
