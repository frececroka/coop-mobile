package de.lorenzgorse.coopmobile

import android.content.Context
import de.lorenzgorse.coopmobile.CoopModule.coopLogin
import de.lorenzgorse.coopmobile.coopclient.CoopClient
import de.lorenzgorse.coopmobile.coopclient.RealCoopClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface CoopClientFactory {
    suspend fun get(context: Context): CoopClient?
    suspend fun refresh(context: Context, invalidateSession: Boolean = false): CoopClient?
    fun clear()
}

class RealCoopClientFactory : CoopClientFactory {

    private var instance: CoopClient? = null

    private val refreshMtx = Mutex()
    override suspend fun get(context: Context): CoopClient? = refreshMtx.withLock {
        if (instance == null) {
            refresh(context)
        }
        return instance
    }

    override suspend fun refresh(context: Context, invalidateSession: Boolean): CoopClient? {
        val sessionId = newSession(context, invalidateSession) ?: return null
        return RealCoopClient(sessionId).also { instance = it }
    }

    private val sessionMtx = Mutex()
    private suspend fun newSession(context: Context, invalidateSession: Boolean): String? = sessionMtx.withLock {
        val sessionId = if (invalidateSession) {
            newSessionFromSavedCredentials(context)
        } else {
            loadSavedSession(context) ?: newSessionFromSavedCredentials(context)
        }
        if (sessionId != null) {
            writeSession(context, sessionId)
        }
        sessionId
    }

    override fun clear() {
        instance = null
    }

}

suspend fun newSessionFromSavedCredentials(context: Context): String? {
    val (username, password) = loadSavedCredentials(context) ?: return null
    return coopLogin.login(username, password)
}
