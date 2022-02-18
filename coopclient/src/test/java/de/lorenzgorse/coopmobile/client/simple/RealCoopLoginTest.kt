package de.lorenzgorse.coopmobile.client.simple

import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test

class RealCoopLoginTest {

    private val backend = RealBackend()

    @Test
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
        RealCoopLogin().login(username, password, CoopLogin.Origin.Manual)

}
