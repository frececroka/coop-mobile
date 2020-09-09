package de.lorenzgorse.coopmobile

import android.content.Context
import java.io.File
import java.io.FileNotFoundException

fun loadSavedSession(context: Context): String? {
    return context.getCoopSharedPreferences().getString("session", null)
}

fun writeSession(context: Context, sessionId: String) {
    context.getCoopSharedPreferences().edit().putString("session", sessionId).apply()
}

fun clearSession(context: Context) {
    context.getCoopSharedPreferences().edit().remove("session").apply()
}

fun loadSavedCredentials(context: Context): Pair<String, String>? {
    val username = context.getCoopSharedPreferences().getString("username", null)
    val password = context.getCoopSharedPreferences().getString("password", null)
    return if (username != null && password != null) {
        Pair(username, password)
    } else loadSavedCredentialsFromFile(context)
}

fun loadSavedCredentialsFromFile(context: Context): Pair<String, String>? {
    val loginLines = try {
        context.filesDir.resolve(File("login.txt")).readLines()
    } catch (e: FileNotFoundException) {
        return null
    }

    val username = loginLines[0].trim()
    val password = loginLines[1].trim()
    writeCredentials(context, username, password)

    return Pair(username, password)
}

fun writeCredentials(context: Context, username: String, password: String) {
    context.getCoopSharedPreferences().edit()
        .putString("username", username)
        .putString("password", password)
        .apply()
}

fun clearCredentials(context: Context) {
    context.getCoopSharedPreferences().edit()
        .remove("username")
        .remove("password")
        .apply()
}
