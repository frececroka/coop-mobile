package de.lorenzgorse.coopmobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import androidx.preference.PreferenceManager
import androidx.work.*
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.Either
import de.lorenzgorse.coopmobile.client.UnitValue
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.components.Fuse
import de.lorenzgorse.coopmobile.ui.consumption.ConsumptionLogCache
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class BalanceCheckWorker(
    context: Context,
    params: WorkerParameters,
    client: CoopClient,
) : CoroutineWorker(context, params) {

    companion object {
        private const val uniqueWorkId = "checkBalance"

        fun enqueueIfEnabled(context: Context) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            if (sharedPreferences.getBoolean("check_balance", false)) {
                enqueue(context)
            }
        }

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workRequest = PeriodicWorkRequestBuilder<BalanceCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            workManager(context).enqueueUniquePeriodicWork(
                uniqueWorkId, ExistingPeriodicWorkPolicy.KEEP, workRequest
            )
        }

        fun cancel(context: Context) {
            workManager(context).cancelUniqueWork(uniqueWorkId)
        }

        private fun workManager(context: Context) = WorkManager.getInstance(context)
    }

    private val balanceCheck =
        BalanceCheck(context, client, RealFirebaseAnalytics(Firebase.analytics))

    override suspend fun doWork(): Result =
        if (balanceCheck.checkBalance()) Result.success() else Result.failure()

}

class BalanceCheck(
    private val context: Context,
    private val client: CoopClient,
    private val analytics: FirebaseAnalytics,
) {

    companion object {
        private const val channelId = "BALANCE_CHECK"
    }

    private val log = LoggerFactory.getLogger(BalanceCheck::class.java)!!
    private val notificationFuse = Fuse(context, "checkBalance")
    private val consumptionLogCache = ConsumptionLogCache(context)

    suspend fun checkBalance(): Boolean {
        val fuseOld = notificationFuse.isBurnt()
        fun logEvent(coopError: CoopError? = null) {
            val result = coopError?.let(::coopErrorToAnalyticsResult) ?: "Success"
            analytics.logEvent(
                "LowBalance_Check",
                bundleOf(
                    "Result" to result,
                    "FuseOld" to fuseOld,
                    "FuseNew" to notificationFuse.isBurnt()
                )
            )
        }

        val lowBalance = when (val result = isBalanceLow()) {
            is Either.Left -> {
                log.error("Loading consumption failed: ${result.value}")
                logEvent(result.value)
                return false
            }
            is Either.Right -> result.value
        }

        if (lowBalance != null) {
            if (!notificationFuse.isBurnt()) {
                showLowBalanceNotification(lowBalance)
                notificationFuse.burn()
            }
        } else {
            notificationFuse.mend()
        }

        logEvent()
        return true
    }

    private suspend fun isBalanceLow(): Either<CoopError, UnitValue<Float>?> {
        val consumption = when (val result = client.getConsumption()) {
            is Either.Left -> return result
            is Either.Right -> result.value
        }

        val consumptionLog = when (val result = client.getConsumptionLog()) {
            is Either.Left -> return result
            is Either.Right -> result.value
        }

        if (consumptionLog != null) {
            consumptionLogCache.insert(consumptionLog)
        }

        val credit = consumption.firstOrNull { it.unit == "CHF" }
            ?: return Either.Left(CoopError.Other("NoMoneyItem"))

        val balanceIslow = credit.amount < balanceThreshold()
        return Either.Right(if (balanceIslow) credit else null)
    }

    private fun balanceThreshold(): Float {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString("check_balance_threshold", "5")!!.toFloat()
    }

    private fun showLowBalanceNotification(credit: UnitValue<Float>) {
        analytics.logEvent("LowBalance_Notification", bundleOf())
        val notificationManager = NotificationManagerCompat.from(context)
        setupNotificationChannel(notificationManager)
        val notification = createNotification(credit)
        notificationManager.notify(0, notification)
    }

    private fun setupNotificationChannel(notificationManager: NotificationManagerCompat) {
        val channel = NotificationChannel(
            channelId, "Balance Check",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(credit: UnitValue<Float>): Notification =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_euro)
            .setContentTitle(context.getString(R.string.title_balance_low))
            .setContentText(
                context.getString(
                    R.string.message_balance_low,
                    credit.amount,
                    credit.unit
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

}
