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
import arrow.core.Either
import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.LabelledAmount
import de.lorenzgorse.coopmobile.client.LabelledAmounts
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.components.Fuse
import de.lorenzgorse.coopmobile.ui.consumption.ConsumptionLogCache
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class BalanceCheckWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val uniqueWorkId = "checkBalance"
        private val log = LoggerFactory.getLogger(BalanceCheckWorker::class.java)

        fun enqueueIfEnabled(context: Context) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val balanceCheckEnabled = sharedPreferences.getBoolean("check_balance", false)
            log.info("Balance check is enabled: $balanceCheckEnabled")
            if (balanceCheckEnabled) {
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

    @Inject
    lateinit var client: CoopClient

    @Inject
    lateinit var analytics: FirebaseAnalytics

    init {
        context.coopComponent().inject(this)
    }

    override suspend fun doWork(): Result {
        val balanceCheck = BalanceCheck(context, client, analytics)
        return if (balanceCheck.checkBalance()) {
            Result.success()
        } else {
            Result.failure()
        }
    }

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

    private suspend fun isBalanceLow(): Either<CoopError, LabelledAmount?> {
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

        val credit = consumption.firstOrNull { it.kind == LabelledAmounts.Kind.Credit }
            ?: return Either.Left(CoopError.Other("NoCreditItem"))
        val creditValue = credit.labelledAmounts.firstOrNull()
            ?: return Either.Left(CoopError.Other("NoCreditValue"))

        val balanceIslow = creditValue.amount.value < balanceThreshold()
        return Either.Right(if (balanceIslow) creditValue else null)
    }

    private fun balanceThreshold(): Float {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString("check_balance_threshold", "5")!!.toFloat()
    }

    private fun showLowBalanceNotification(credit: LabelledAmount) {
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

    private fun createNotification(credit: LabelledAmount): Notification =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_euro)
            .setContentTitle(context.getString(R.string.title_balance_low))
            .setContentText(
                context.getString(
                    R.string.message_balance_low,
                    credit.amount.value,
                    credit.amount.unit,
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

}
