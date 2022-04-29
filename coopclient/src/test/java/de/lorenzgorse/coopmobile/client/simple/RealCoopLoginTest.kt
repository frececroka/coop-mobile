package de.lorenzgorse.coopmobile.client.simple

import de.lorenzgorse.coopmobile.client.Config
import kotlinx.coroutines.runBlocking
import okhttp3.CookieJar
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Ignore
import org.junit.Test

class RealCoopLoginTest {

    private val backend = RealBackend()

    @Test
    @Ignore("I don't have a CoopMobile account at the moment")
    fun testLogin() = runBlocking {
        val sessionId = login(backend.username, backend.password)
        MatcherAssert.assertThat(sessionId, Matchers.not(Matchers.nullValue()))
    }

    @Test
    fun testLoginWrongUsername() = runBlocking {
        val sessionId = login(backend.wrongUsername, backend.password)
        MatcherAssert.assertThat(sessionId, Matchers.nullValue())
    }

    @Test
    fun testLoginWrongPassword() = runBlocking {
        val sessionId = login(backend.username, backend.wrongPassword)
        MatcherAssert.assertThat(sessionId, Matchers.nullValue())
    }

    private suspend fun login(username: String, password: String): String? =
        RealCoopLogin(Config(), ::httpClientFactory).login(username, password, CoopLogin.Origin.Manual)

}
