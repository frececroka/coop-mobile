package de.lorenzgorse.coopmobile

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.testing.FragmentScenario
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import de.lorenzgorse.coopmobile.CoopModule.firstInstallTimeProvider
import de.lorenzgorse.coopmobile.MockCoopData.coopData1
import de.lorenzgorse.coopmobile.MockCoopData.coopData2
import de.lorenzgorse.coopmobile.coopclient.CoopException.HtmlChanged
import de.lorenzgorse.coopmobile.coopclient.CoopException.PlanUnsupported
import de.lorenzgorse.coopmobile.ui.overview.OverviewFragment
import de.lorenzgorse.coopmobile.ui.overview.StaticFirstInstallTimeProvider
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.*
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.*
import java.net.UnknownHostException

class OverviewFragmentTest {

    @get:Rule
    val intentsTestRule = IntentsTestRule(FragmentScenario.EmptyFragmentActivity::class.java)

    private lateinit var navController: NavController
    private lateinit var scenario: FragmentScenario<OverviewFragment>

    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val prefs = context.getCoopSharedPreferences()

    @Test
    fun testHappyPath() { runBlocking {
        mockPreparedCoopClient()

        setLoadCount(5)

        launchFragment()

        onView(withId(R.id.textCreditUnit)).check(matches(withText("CHF")))
        onView(withId(R.id.textCreditValue)).check(matches(withText("123,45")))
        onView(withId(R.id.consumptions)).check(matches(hasChildCount(3)))

        assertThat(loadCount(), `is`(6))
        onView(withId(R.id.bannerRate)).check(matches(not(isDisplayed())))
    } }

    @Test
    fun testRateBanner() { runBlocking {
        mockPreparedCoopClient()

        setFirstInstallTime(5.1)
        setLoadCount(9)
        unsetRatingBannerDismissed()

        launchFragment()

        assertThat(loadCount(), `is`(10))
        onView(withId(R.id.bannerRate)).check(matches(isDisplayed()))

        onView(withId(R.id.btNo)).perform(click())

        onView(withId(R.id.bannerRate)).check(matches(not(isDisplayed())))
        assertThat(ratingBannerDismissed(), `is`(true))
    } }

    @Test
    fun testRateBannerOk() { runBlocking {
        mockPreparedCoopClient()

        setFirstInstallTime(5.1)
        setLoadCount(9)
        unsetRatingBannerDismissed()

        intending(hasAction(Intent.ACTION_VIEW))
            .respondWith(ActivityResult(Activity.RESULT_OK, Intent()))

        launchFragment()

        onView(withId(R.id.btOkay)).perform(click())

        onView(withId(R.id.bannerRate)).check(matches(not(isDisplayed())))
        assertThat(ratingBannerDismissed(), `is`(true))

        intended(allOf(
            hasAction(Intent.ACTION_VIEW),
            hasData(Uri.parse("market://details?id=de.lorenzgorse.coopmobile"))))
    } }

    @Test
    fun testRateBannerDismissed() { runBlocking {
        mockPreparedCoopClient()

        setFirstInstallTime(5.1)
        setLoadCount(90)
        setRatingBannerDismissed()

        launchFragment()

        assertThat(loadCount(), `is`(91))
        onView(withId(R.id.bannerRate)).check(matches(not(isDisplayed())))
    } }

    @Test
    fun testRateBannerTooSoon() { runBlocking {
        mockPreparedCoopClient()

        setFirstInstallTime(4.9)
        setLoadCount(90)
        unsetRatingBannerDismissed()

        launchFragment()

        assertThat(loadCount(), `is`(91))
        onView(withId(R.id.bannerRate)).check(matches(not(isDisplayed())))
    } }

    @Test
    fun noNetwork() { runBlocking {
        val coopClient = mockCoopClient()
        `when`(coopClient.getConsumption()).thenThrow(UnknownHostException())

        setLoadCount(5)

        launchFragment()

        onView(withId(R.id.loading)).check(matches(not(isDisplayed())))
        onView(withId(R.id.layContent)).check(matches(not(isDisplayed())))
        onView(withId(R.id.layError)).check(matches(isDisplayed()))
        onView(withId(R.id.txtNoNetwork)).check(matches(isDisplayed()))
        onView(withId(R.id.txtUpdate)).check(matches(not(isDisplayed())))
        onView(withId(R.id.txtPlanUnsupported)).check(matches(not(isDisplayed())))

        assertThat(loadCount(), `is`(5))
    } }

    @Test
    fun refreshSession() { runBlocking {
        val coopClient = mockExpiredCoopClient()
        `when`(coopClient.getConsumption()).thenReturn(coopData1)

        launchFragment()

        onView(withId(R.id.loading)).check(matches(not(isDisplayed())))
        onView(withId(R.id.layContent)).check(matches(isDisplayed()))
        onView(withId(R.id.layError)).check(matches(not(isDisplayed())))
        verify(coopClient).getConsumption()
    } }

    @Test
    fun htmlChanged() { runBlocking {
        val coopClient = mockCoopClient()
        `when`(coopClient.getConsumption()).thenThrow(HtmlChanged(Exception()))

        launchFragment()

        onView(withId(R.id.loading)).check(matches(not(isDisplayed())))
        onView(withId(R.id.layContent)).check(matches(not(isDisplayed())))
        onView(withId(R.id.layError)).check(matches(isDisplayed()))
        onView(withId(R.id.txtNoNetwork)).check(matches(not(isDisplayed())))
        onView(withId(R.id.txtUpdate)).check(matches(isDisplayed()))
        onView(withId(R.id.txtPlanUnsupported)).check(matches(not(isDisplayed())))
    } }

    @Test
    fun planUnsupported() { runBlocking {
        val coopClient = mockCoopClient()
        `when`(coopClient.getConsumption()).thenThrow(PlanUnsupported("wireless"))

        launchFragment()

        onView(withId(R.id.loading)).check(matches(not(isDisplayed())))
        onView(withId(R.id.layContent)).check(matches(not(isDisplayed())))
        onView(withId(R.id.layError)).check(matches(isDisplayed()))
        onView(withId(R.id.txtNoNetwork)).check(matches(not(isDisplayed())))
        onView(withId(R.id.txtUpdate)).check(matches(not(isDisplayed())))
        onView(withId(R.id.txtPlanUnsupported)).check(matches(isDisplayed()))
    } }

    @Test
    fun refresh() { runBlocking {
        val coopClient = mockCoopClient()
        `when`(coopClient.getConsumption()).thenReturn(coopData1).thenReturn(coopData2)

        launchFragment()

        onView(withId(R.id.loading)).check(matches(not(isDisplayed())))

        onView(withId(R.id.textCreditUnit)).check(matches(withText("CHF")))
        onView(withId(R.id.textCreditValue)).check(matches(withText("123,45")))
        onView(withId(R.id.consumptions)).check(matches(hasChildCount(3)))

        scenario.selectMenuItem(R.id.itRefresh)

        onView(withId(R.id.loading)).check(matches(not(isDisplayed())))

        onView(withId(R.id.textCreditUnit)).check(matches(withText("CHF")))
        onView(withId(R.id.textCreditValue)).check(matches(withText("121,45")))
        onView(withId(R.id.consumptions)).check(matches(hasChildCount(2)))
    } }

    private fun launchFragment() {
        navController = mock(NavController::class.java)
        scenario = FragmentScenario.launchInContainer(
            OverviewFragment::class.java, Bundle.EMPTY, R.style.AppTheme, FragmentFactory())
        scenario.onFragment { Navigation.setViewNavController(it.requireView(), navController) }
    }

    private fun setLoadCount(loadCount: Int) {
        prefs.edit().putInt("load_count", loadCount).apply()
    }

    private fun loadCount(): Int {
        return prefs.getInt("load_count", -1)
    }

    private fun setRatingBannerDismissed() {
        prefs.edit().putBoolean("rating_banner_dismissed", true).apply()
    }

    private fun unsetRatingBannerDismissed() {
        prefs.edit().remove("rating_banner_dismissed").apply()
    }

    private fun ratingBannerDismissed(): Boolean {
        return prefs.getBoolean("rating_banner_dismissed", false)
    }

    private fun setFirstInstallTime(days: Double) {
        val firstInstallTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000).toLong()
        firstInstallTimeProvider = StaticFirstInstallTimeProvider(firstInstallTime)
    }

}
