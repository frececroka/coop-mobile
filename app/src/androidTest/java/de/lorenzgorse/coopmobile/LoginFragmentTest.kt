package de.lorenzgorse.coopmobile

import android.os.Bundle
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import de.lorenzgorse.coopmobile.fragments.LoginFragment
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.*
import java.net.UnknownHostException

class LoginFragmentTest {

    @get:Rule
    val restoreCoopModule = RestoreCoopModule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val username = "0781234567"
    private val password = "supersecret"

    private lateinit var navController: NavController
    private lateinit var scenario: FragmentScenario<LoginFragment>

    @Before
    fun before() {
        navController = mock(NavController::class.java)
        scenario = launchFragmentInContainer<LoginFragment>(
            Bundle.EMPTY, R.style.Base_Theme_AppCompat, FragmentFactory())
        scenario.onFragment { Navigation.setViewNavController(it.requireView(), navController) }
    }

    @Test
    fun login() { runBlocking {
        val coopLogin = mockCoopLogin()
        val sessionId = "2384234820943"
        `when`(coopLogin.login(username, password)).thenReturn(sessionId)
        doLogin()
        verify(navController).navigate(R.id.action_overview)
        assertThat(loadSavedSession(context), equalTo(sessionId))
        assertThat(loadSavedCredentials(context), equalTo(Pair(username, password)))
    } }

    @Test
    fun loginNoNetwork() { runBlocking {
        val coopLogin = mockCoopLogin()
        `when`(coopLogin.login(anyString(), anyString())).thenThrow(UnknownHostException())
        doLogin()
        onView(withId(R.id.txtNoNetwork)).check(matches(isDisplayed()))
        onView(withId(R.id.txtLoginFailed)).check(matches(not(isDisplayed())))
    } }

    @Test
    fun loginErrorResponse() { runBlocking {
        val coopLogin = mockCoopLogin()
        `when`(coopLogin.login(anyString(), anyString())).thenReturn(null)
        doLogin()
        onView(withId(R.id.txtNoNetwork)).check(matches(not(isDisplayed())))
        onView(withId(R.id.txtLoginFailed)).check(matches(isDisplayed()))
    } }

    private fun doLogin() {
        onView(withId(R.id.txtUsername)).perform(typeText(username))
        onView(withId(R.id.txtPassword)).perform(typeText(password), closeSoftKeyboard())
        onView(withId(R.id.btLogin)).perform(click())
    }

}
