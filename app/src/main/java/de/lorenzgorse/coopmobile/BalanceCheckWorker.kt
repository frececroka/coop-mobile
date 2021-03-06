package de.lorenzgorse.coopmobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import androidx.work.*
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.components.Fuse
import de.lorenzgorse.coopmobile.coopclient.UnitValue
import de.lorenzgorse.coopmobile.data.Either
import de.lorenzgorse.coopmobile.data.loadData
import de.lorenzgorse.coopmobile.ui.consumption.ConsumptionLogCache
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class BalanceCheckWorker(
    private val context: Context,
    params: WorkerParameters
): CoroutineWorker(context, params) {

    companion object {
        val log = LoggerFactory.getLogger(BalanceCheckWorker::class.java)!!

        private const val uniqueWorkId = "checkBalance"
        private const val channelId = "BALANCE_CHECK"

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
                uniqueWorkId, ExistingPeriodicWorkPolicy.KEEP, workRequest)
        }

        fun cancel(context: Context) {
            workManager(context).cancelUniqueWork(uniqueWorkId)
        }

        private fun workManager(context: Context) = WorkManager.getInstance(context)
    }

    private val analytics = FirebaseAnalytics.getInstance(context)
    private val notificationFuse = Fuse(context, "checkBalance")
    private val consumptionLogCache = ConsumptionLogCache(context)

    override suspend fun doWork(): Result {
        analytics.logEvent("periodically_check_balance", null)

        val (data, consumptionLog) = when (val result = loadData(context) { client ->
            Pair(client.getConsumption(), client.getConsumptionLog())
        }) {
            is Either.Right -> result.value
            is Either.Left -> {
                log.error("Checking balance failed: ${result.value}")
                return Result.failure()
            }
        }

        if (consumptionLog != null) {
            consumptionLogCache.insert(consumptionLog)
        }

        val credit = data.firstOrNull { it.unit == "CHF" } ?: return Result.success()
        if (credit.amount < balanceThreshold()) {
            if (!notificationFuse.isBurnt()) {
                showLowBalanceNotification(credit)
                notificationFuse.burn()
            }
        } else {
            notificationFuse.mend()
        }

        return Result.success()
    }

    private fun balanceThreshold(): Float {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString("check_balance_threshold", "5")!!.toFloat()
    }

    private fun showLowBalanceNotification(credit: UnitValue<Float>) {
        val notificationManager = NotificationManagerCompat.from(context)
        setupNotificationChannel(notificationManager)
        val notification = createNotification(credit)
        notificationManager.notify(0, notification)
    }

    private fun setupNotificationChannel(notificationManager: NotificationManagerCompat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Balance Check",
                NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(credit: UnitValue<Float>): Notification =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_euro)
            .setContentTitle(context.getString(R.string.title_balance_low))
            .setContentText(context.getString(R.string.message_balance_low, credit.amount, credit.unit))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

}
