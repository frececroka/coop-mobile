package de.lorenzgorse.coopmobile

import android.app.NotificationManager
import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.lorenzgorse.coopmobile.client.*
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.components.Fuse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestApplication::class)
class BalanceCheckTest {

    private val context: TestApplication = ApplicationProvider.getApplicationContext()!!
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val notificationManager =
        shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
    private val notificationFuse = Fuse(context, "checkBalance")

    private lateinit var analytics: FakeFirebaseAnalytics
    private lateinit var balanceCheck: BalanceCheck

    @Test
    fun testBalanceHigh(): Unit = runBlocking {
        setup(Either.Right(listOf(UnitValue("Guthaben", 12F, "CHF"))))

        balanceCheck.checkBalance()
        assertThat(notificationManager.activeNotifications, emptyArray())
        assertThat(notificationFuse.isBurnt(), equalTo(false))
        analytics.matchEvents(checkEvent("Success", false, false))
    }

    @Test
    fun testBalanceHighMendsFuse(): Unit = runBlocking {
        setup(Either.Right(listOf(UnitValue("Guthaben", 12F, "CHF"))))
        notificationFuse.burn()

        balanceCheck.checkBalance()
        assertThat(notificationManager.activeNotifications, emptyArray())
        assertThat(notificationFuse.isBurnt(), equalTo(false))
        analytics.matchEvents(checkEvent("Success", true, false))
    }

    @Test
    fun testBalanceLow(): Unit = runBlocking {
        setup(Either.Right(listOf(UnitValue("Guthaben", 4F, "CHF"))))

        balanceCheck.checkBalance()
        assertThat(notificationManager.activeNotifications, arrayWithSize(1))
        assertThat(notificationFuse.isBurnt(), equalTo(true))
        analytics.matchEvents(
            notifyEvent(),
            checkEvent("Success", false, true)
        )
    }

    @Test
    fun testBalanceLowAlreadyNotified(): Unit = runBlocking {
        setup(Either.Right(listOf(UnitValue("Guthaben", 4F, "CHF"))))
        notificationFuse.burn()

        balanceCheck.checkBalance()
        assertThat(notificationManager.activeNotifications, emptyArray())
        assertThat(notificationFuse.isBurnt(), equalTo(true))
        analytics.matchEvents(checkEvent("Success", true, true))
    }

    @Test
    fun testBalanceLowExplicitThreshold(): Unit = runBlocking {
        setup(Either.Right(listOf(UnitValue("Guthaben", 12F, "CHF"))))
        setBalanceThreshold(13F)

        balanceCheck.checkBalance()
        assertThat(notificationManager.activeNotifications, arrayWithSize(1))
        assertThat(notificationFuse.isBurnt(), equalTo(true))
    }

    @Test
    fun testError(): Unit = runBlocking {
        setup(Either.Left(CoopError.NoNetwork))

        balanceCheck.checkBalance()
        assertThat(notificationManager.activeNotifications, emptyArray())
        assertThat(notificationFuse.isBurnt(), equalTo(false))
        analytics.matchEvents(checkEvent("NoNetwork", false, false))
    }

    private fun setup(consumption: Either<CoopError, List<UnitValue<Float>>>) {
        val coopClient = mockk<CoopClient>()
        coEvery { coopClient.getConsumption() } returns consumption
        coEvery { coopClient.getConsumptionLog() } returns Either.Right(emptyList())
        analytics = FakeFirebaseAnalytics()
        balanceCheck = BalanceCheck(context, coopClient, analytics)
    }

    private fun setBalanceThreshold(value: Float) {
        sharedPreferences.edit().putString("check_balance_threshold", value.toString()).apply()
    }

    private fun checkEvent(
        result: String,
        fuseOld: Boolean,
        fuseNew: Boolean
    ) = FakeFirebaseAnalytics.Invocation(
        "LowBalance_Check",
        mapOf(
            "Result" to result,
            "FuseOld" to fuseOld,
            "FuseNew" to fuseNew,
        )
    )

    private fun notifyEvent() = FakeFirebaseAnalytics.Invocation("LowBalance_Notification", mapOf())

}
