package de.lorenzgorse.coopmobile.preferences

import android.content.Context
import de.lorenzgorse.coopmobile.client.refreshing.CredentialsStore
import java.io.File
import java.io.FileNotFoundException

class SharedPreferencesCredentialsStore(private val context: Context) : CredentialsStore {

    fun setCredentials(username: String, password: String) {
        context.getCoopSharedPreferences().edit()
            .putString("username", username)
            .putString("password", password)
            .apply()
    }

    override fun loadCredentials(): Pair<String, String>? {
        val username = context.getCoopSharedPreferences().getString("username", null)
        val password = context.getCoopSharedPreferences().getString("password", null)
        return if (username != null && password != null) {
            Pair(username, password)
        } else loadCredentialsFromFile()
    }

    private fun loadCredentialsFromFile(): Pair<String, String>? {
        val loginLines = try {
            context.filesDir.resolve(File("login.txt")).readLines()
        } catch (e: FileNotFoundException) {
            return null
        }

        val username = loginLines[0].trim()
        val password = loginLines[1].trim()
        setCredentials(username, password)

        return Pair(username, password)
    }

    fun clearCredentials() {
        context.getCoopSharedPreferences().edit()
            .remove("username")
            .remove("password")
            .apply()
    }

    override fun setSession(sessionId: String) {
        context.getCoopSharedPreferences().edit().putString("session", sessionId).apply()
    }

    override fun loadSession(): String? {
        return context.getCoopSharedPreferences().getString("session", null)
    }

    fun clearSession() {
        context.getCoopSharedPreferences().edit().remove("session").apply()
    }

}
