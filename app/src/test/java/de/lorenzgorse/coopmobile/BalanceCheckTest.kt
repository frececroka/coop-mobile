package de.lorenzgorse.coopmobile

import android.app.NotificationManager
import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.lorenzgorse.coopmobile.client.*
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.components.Fuse
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

    private lateinit var balanceCheck: BalanceCheck

    @Test
    fun testBalanceHigh(): Unit = runBlocking {
        setup(Either.Right(listOf(UnitValue("Guthaben", 12F, "CHF"))))

        balanceCheck.checkBalance()
        assertThat(notificationManager.activeNotifications, emptyArray())
        assertThat(notificationFuse.isBurnt(), equalTo(false))
    }

    @Test
    fun testBalanceHighMendsFuse(): Unit = runBlocking {
        setup(Either.Right(listOf(UnitValue("Guthaben", 12F, "CHF"))))
        notificationFuse.burn()

        balanceCheck.checkBalance()
        assertThat(notificationManager.activeNotifications, emptyArray())
        assertThat(notificationFuse.isBurnt(), equalTo(false))
    }

    @Test
    fun testBalanceLow(): Unit = runBlocking {
        setup(Either.Right(listOf(UnitValue("Guthaben", 4F, "CHF"))))

        balanceCheck.checkBalance()
        assertThat(notificationManager.activeNotifications, arrayWithSize(1))
        assertThat(notificationFuse.isBurnt(), equalTo(true))
    }

    @Test
    fun testBalanceLowAlreadyNotified(): Unit = runBlocking {
        setup(Either.Right(listOf(UnitValue("Guthaben", 4F, "CHF"))))
        notificationFuse.burn()

        balanceCheck.checkBalance()
        assertThat(notificationManager.activeNotifications, emptyArray())
        assertThat(notificationFuse.isBurnt(), equalTo(true))
    }

    @Test
    fun testBalanceLowExplicitThreshold(): Unit = runBlocking {
        setup(Either.Right(listOf(UnitValue("Guthaben", 12F, "CHF"))))
        setBalanceThreshold(13F)

        balanceCheck.checkBalance()
        assertThat(notificationManager.activeNotifications, arrayWithSize(1))
        assertThat(notificationFuse.isBurnt(), equalTo(true))
    }

    private fun setup(consumption: Either<CoopError, List<UnitValue<Float>>>) {
        val coopClient = FakeCoopClient(consumption)
        balanceCheck = BalanceCheck(context, coopClient)
    }

    private fun setBalanceThreshold(value: Float) {
        sharedPreferences.edit().putString("check_balance_threshold", value.toString()).apply()
    }

    class FakeCoopClient(
        private val consumption: Either<CoopError, List<UnitValue<Float>>>
    ) : CoopClient {

        override suspend fun getConsumption(): Either<CoopError, List<UnitValue<Float>>> =
            consumption

        override suspend fun getConsumptionLog(): Either<CoopError, List<ConsumptionLogEntry>?> =
            Either.Right(listOf())

        // The other methods are irrelevant

        override suspend fun getProfile(): Either<CoopError, List<Pair<String, String>>> =
            throw NotImplementedError()

        override suspend fun getProducts(): Either<CoopError, List<Product>> =
            throw NotImplementedError()

        override suspend fun buyProduct(buySpec: ProductBuySpec): Either<CoopError, Boolean> =
            throw NotImplementedError()

        override suspend fun getCorrespondeces(): Either<CoopError, List<CorrespondenceHeader>> =
            throw NotImplementedError()

        override suspend fun augmentCorrespondence(header: CorrespondenceHeader): Either<CoopError, Correspondence> =
            throw NotImplementedError()

        override suspend fun sessionId(): String? =
            throw NotImplementedError()

    }

}
