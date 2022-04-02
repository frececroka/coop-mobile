package de.lorenzgorse.coopmobile

import android.os.Bundle
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.lorenzgorse.coopmobile.client.simple.CoopException
import de.lorenzgorse.coopmobile.client.simple.CoopLogin
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference


@RunWith(AndroidJUnit4::class)
@Config(application = TestApplication::class)
class MonitoredCoopLoginTest {

    private val context: TestApplication = getApplicationContext()!!
    private lateinit var monitoredCoopLogin: MonitoredCoopLogin
    private lateinit var firebaseAnalytics: FakeFirebaseAnalytics
    private lateinit var coopLogin: FakeCoopLogin
    private lateinit var userProperties: UserProperties

    fun setup(vararg loginInvocations: FakeCoopLogin.Invocation) {
        coopLogin = FakeCoopLogin(*loginInvocations)
        firebaseAnalytics = FakeFirebaseAnalytics()
        userProperties = UserProperties(context)
        monitoredCoopLogin =
            MonitoredCoopLogin(context, userProperties, coopLogin, firebaseAnalytics)
    }

    @Test
    fun testNewUserOneAttempt(): Unit = runBlocking {
        setup(successfulLogin, successfulLogin)

        monitoredCoopLogin.login("username", "password", CoopLogin.Origin.Manual)

        firebaseAnalytics.matchEvents(
            loginEvent("Manual", "Success", true)
        )

        monitoredCoopLogin.login("username", "password", CoopLogin.Origin.Manual)

        firebaseAnalytics.matchEvents(
            loginEvent("Manual", "Success", false)
        )
    }

    @Test
    fun testNewUserTwoAttempts(): Unit = runBlocking {
        setup(failedLogin, successfulLogin)

        monitoredCoopLogin.login("username", "password", CoopLogin.Origin.Manual)

        firebaseAnalytics.matchEvents(
            loginEvent("Manual", "AuthFailed", true)
        )

        monitoredCoopLogin.login("username", "password", CoopLogin.Origin.Manual)

        firebaseAnalytics.matchEvents(
            loginEvent("Manual", "Success", true)
        )
    }

    @Test
    fun testMigratesOnbLoginEvent(): Unit = runBlocking {
        File(context.filesDir, "Onb_Login_Success").writeText(System.currentTimeMillis().toString())

        setup(successfulLogin)

        monitoredCoopLogin.login("username", "password", CoopLogin.Origin.Manual)

        firebaseAnalytics.matchEvents(
            migrationEvent(),
            loginEvent("Manual", "Success", false)
        )

        assertThat(File(context.filesDir, "Onb_Login_Success").exists(), equalTo(false))
    }

    @Test
    fun testReportsOrigin() = runBlocking {
        // Log in once to burn the newUser fuse.
        // TODO: use hamcrest matchers for matchEvents instead
        setup(successfulLogin)
        monitoredCoopLogin.login("username", "password", CoopLogin.Origin.Manual)

        for (origin in CoopLogin.Origin.values()) {
            setup(successfulLogin.copy(origin = origin))
            monitoredCoopLogin.login("username", "password", origin)
            firebaseAnalytics.matchEvents(loginEvent(origin.name, "Success", false))
        }
    }

    @Test
    fun testReportsAuthFailedError() = runBlocking {
        setup(failedLogin)
        monitoredCoopLogin.login("username", "password", CoopLogin.Origin.Manual)
        firebaseAnalytics.matchEvents(loginEvent("Manual", "AuthFailed", true))
    }

    @Test
    fun testReportsIOException(): Unit = runBlocking {
        setup(noNetworkLogin)

        try {
            monitoredCoopLogin.login("username", "password", CoopLogin.Origin.Manual)
            fail()
        } catch (e: IOException) {
        }

        firebaseAnalytics.matchEvents(
            loginEvent("Manual", "NoNetwork", true)
        )
    }

    @Test
    fun testReportsHtmlChanged(): Unit = runBlocking {
        setup(htmlChangedLogin)

        try {
            monitoredCoopLogin.login("username", "password", CoopLogin.Origin.Manual)
            fail()
        } catch (e: CoopException.HtmlChanged) {
        }

        firebaseAnalytics.matchEvents(
            loginEvent("Manual", "HtmlChanged", true)
        )
    }

    @Test
    fun testSetsUserProperties(): Unit = runBlocking {
        setup(successfulLogin)
        assertThat(userProperties.data(), equalTo(UserProperties.Data()))
        monitoredCoopLogin.login("username", "password", CoopLogin.Origin.Manual)
        assertThat(userProperties.data(), equalTo(UserProperties.Data(plan = "prepaid")))
    }

    private val successfulLogin = FakeCoopLogin.Invocation(
        "username",
        "password",
        CoopLogin.Origin.Manual,
        "12345",
        "prepaid",
    )

    private val failedLogin = FakeCoopLogin.Invocation(
        "username",
        "password",
        CoopLogin.Origin.Manual,
        null,
    )

    private val noNetworkLogin = FakeCoopLogin.Invocation(
        "username",
        "password",
        CoopLogin.Origin.Manual,
        null,
        maybeThrow = IOException()
    )

    private val htmlChangedLogin = FakeCoopLogin.Invocation(
        "username",
        "password",
        CoopLogin.Origin.Manual,
        null,
        maybeThrow = CoopException.HtmlChanged()
    )

    private fun loginEvent(origin: String, status: String, newUser: Boolean) =
        FakeFirebaseAnalytics.Invocation(
            "Login",
            mapOf(
                "Origin" to origin,
                "Status" to status,
                "NewUser" to newUser,
            )
        )

    private fun migrationEvent() =
        FakeFirebaseAnalytics.Invocation(
            "Migration",
            mapOf(
                "Feature" to "Onb_Login_Success",
            )
        )

}

class FakeCoopLogin(private val invocations: Iterator<Invocation>) : CoopLogin {

    constructor(vararg invocations: Invocation) : this(invocations.iterator())

    data class Invocation(
        val username: String,
        val password: String,
        val origin: CoopLogin.Origin,
        val sessionId: String?,
        val plan: String? = null,
        val maybeThrow: Exception? = null,
    )

    override suspend fun login(
        username: String,
        password: String,
        origin: CoopLogin.Origin,
        plan: AtomicReference<String?>?
    ): String? {
        val invocation = invocations.next()
        assertThat(username, equalTo(invocation.username))
        assertThat(password, equalTo(invocation.password))
        assertThat(origin, equalTo(invocation.origin))
        invocation.maybeThrow?.let { throw it }
        plan?.set(invocation.plan)
        return invocation.sessionId
    }

}

class FakeFirebaseAnalytics : FirebaseAnalytics {

    data class Invocation(val name: String, val params: Map<String, Any?>?)

    private val events = arrayListOf<Invocation>()

    override fun logEvent(name: String, params: Bundle?) {
        val paramsMap = params?.keySet()?.associate { Pair(it, params.get(it)) }
        events.add(Invocation(name, paramsMap))
    }

    fun matchEvents(matcher: Matcher<List<Invocation>>) {
        assertThat(events, matcher)
        events.clear()
    }

    fun matchEvents(vararg invocations: Invocation) {
        matchEvents(equalTo(invocations.toList()))
    }

}
