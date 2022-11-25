package de.lorenzgorse.coopmobile.client.refreshing

import arrow.core.Either
import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.LabelledAmounts
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.client.simple.CoopLogin
import de.lorenzgorse.coopmobile.client.simple.CoopLogin.Origin.SessionRefresh
import io.mockk.MockKStubScope
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import java.io.IOException
import java.util.*

class RefreshingSessionCoopClientTest {

    private val credentialsStore = InMemoryCredentialStore()
    private val staticSessionCoopClientFactory = StaticSessionCoopClientFactory()
    private val coopLogin = mockk<CoopLogin>()
    private val coopClientFactory = RealCoopClientFactory(
        credentialsStore, coopLogin, staticSessionCoopClientFactory::factory
    )
    private val refreshingSessionCoopClient = RefreshingSessionCoopClient(coopClientFactory)

    @Test
    fun testNoClient() {
        credentialsStore.clearCredentials()
        credentialsStore.clearSession()

        assertResult(Either.Left(CoopError.NoClient))
    }

    @Test
    fun testSuccessNewSession() {
        credentialsStore.setCredentials("username", "password")

        coEvery { coopLogin.login("username", "password", SessionRefresh) }.returns("session_id_1")
            .andThenThrows(IllegalStateException())

        val consumptionResult = Either.Right<List<LabelledAmounts>>(listOf())
        staticSessionCoopClientFactory.expect("session_id_1") {
            returns(consumptionResult)
                .andThenThrows(IllegalStateException())
        }

        assertResult(consumptionResult)
    }

    @Test
    fun testSuccessExistingSession() {
        credentialsStore.setCredentials("username", "password")
        credentialsStore.setSession("session_id_1")

        val consumptionResult = Either.Right<List<LabelledAmounts>>(listOf())
        staticSessionCoopClientFactory.expect("session_id_1") {
            returns(consumptionResult)
                .andThenThrows(IllegalStateException())
        }

        assertResult(consumptionResult)
    }

    @Test
    fun testErrorNoNetwork() {
        credentialsStore.setCredentials("username", "password")
        credentialsStore.setSession("session_id_1")

        staticSessionCoopClientFactory.expect("session_id_1") {
            returns(Either.Left(CoopError.NoNetwork))
                .andThenThrows(IllegalStateException())
        }

        assertResult(Either.Left(CoopError.NoNetwork))
    }

    @Test
    fun testSessionRefreshSuccess() {
        credentialsStore.setCredentials("username", "password")
        credentialsStore.setSession("session_id_1")

        staticSessionCoopClientFactory.expect("session_id_1") {
            returns(Either.Left(CoopError.Unauthorized))
                .andThenThrows(IllegalStateException())
        }

        coEvery { coopLogin.login("username", "password", SessionRefresh) }
            .returns("session_id_2")
            .andThenThrows(IllegalStateException())

        val consumptionResult = Either.Right<List<LabelledAmounts>>(listOf())
        staticSessionCoopClientFactory.expect("session_id_2") {
            returns(consumptionResult)
                .andThenThrows(IllegalStateException())
        }

        assertResult(consumptionResult)
    }

    @Test
    fun testSessionRefreshLoginNoNetwork() {
        credentialsStore.setCredentials("username", "password")
        credentialsStore.setSession("session_id_1")

        staticSessionCoopClientFactory.expect("session_id_1") {
            returns(Either.Left(CoopError.Unauthorized))
                .andThenThrows(IllegalStateException())
        }

        coEvery { coopLogin.login("username", "password", SessionRefresh) }
            .throws(IOException())
            .andThenThrows(IllegalStateException())

        assertResult(Either.Left(CoopError.NoNetwork))
    }

    @Test
    fun testSessionRefreshLoginFailed() {
        credentialsStore.setCredentials("username", "password")
        credentialsStore.setSession("session_id_1")

        staticSessionCoopClientFactory.expect("session_id_1") {
            returns(Either.Left(CoopError.Unauthorized))
                .andThenThrows(IllegalStateException())
        }

        coEvery { coopLogin.login("username", "password", SessionRefresh) }
            .returns(null)
            .andThenThrows(IllegalStateException())

        assertResult(Either.Left(CoopError.FailedLogin))
    }

    @Test
    fun testSessionRefreshUnauthorized() {
        credentialsStore.setCredentials("username", "password")
        credentialsStore.setSession("session_id_1")

        staticSessionCoopClientFactory.expect("session_id_1") {
            returns(Either.Left(CoopError.Unauthorized))
                .andThenThrows(IllegalStateException())
        }

        coEvery { coopLogin.login("username", "password", SessionRefresh) }
            .returns("session_id_2")
            .andThenThrows(IllegalStateException())

        staticSessionCoopClientFactory.expect("session_id_2") {
            returns(Either.Left(CoopError.Unauthorized))
                .andThenThrows(IllegalStateException())
        }

        assertResult(Either.Left(CoopError.Unauthorized))
    }

    private fun assertResult(result1: Either<CoopError, List<LabelledAmounts>>) {
        val result = runBlocking { refreshingSessionCoopClient.getConsumption() }
        assertThat(result, equalTo(result1))
    }

}

class InMemoryCredentialStore : CredentialsStore {
    private var sessionId: String? = null
    private var credentials: Pair<String, String>? = null

    override fun setCredentials(username: String, password: String) {
        credentials = Pair(username, password)
    }

    override fun loadCredentials(): Pair<String, String>? {
        return credentials
    }

    override fun clearCredentials() {
        credentials = null
    }

    override fun setSession(sessionId: String) {
        this.sessionId = sessionId
    }

    override fun loadSession(): String? {
        return sessionId
    }

    override fun clearSession() {
        sessionId = null
    }
}

class StaticSessionCoopClientFactory {
    private val invocations: Queue<Pair<String, CoopClient>> = LinkedList()

    fun expect(
        sessionId: String,
        mock: MockKStubScope<Either<CoopError, List<LabelledAmounts>>, Either<CoopError, List<LabelledAmounts>>>.() -> Unit
    ) {
        val coopClient = mockk<CoopClient>()
        mock(coEvery { coopClient.getConsumption() })
        invocations.add(Pair(sessionId, coopClient))
    }

    fun factory(sessionId: String): CoopClient {
        val invocation = invocations.remove()
        assertThat(sessionId, equalTo(invocation.first))
        return invocation.second
    }
}
