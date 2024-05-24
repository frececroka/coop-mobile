package bifrost

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import de.lorenzgorse.coopmobile.DaggerCoopComponent
import de.lorenzgorse.coopmobile.MainCoopModule
import de.lorenzgorse.coopmobile.app
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Executors
import javax.inject.Inject

// TODO: add global fields from UserProperties
class Meter @Inject constructor(private val context: Context) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val workManager = WorkManager.getInstance(context)
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val db = Room
        .databaseBuilder(context, BifrostDatabase::class.java, "bifrost")
        .fallbackToDestructiveMigration()
        .build()

    private val metricDao = db.metricDao()
    private val sendMetricRequestDao = db.sendMetricRequestDao()
    private val bifrost = Bifrost.bifrost

    private val executor = Executors.newSingleThreadExecutor()

    fun increment(name: String, fields: Map<String, String>) {
        executor.submit { incrementSync(name, fields) }
    }

    private fun incrementSync(name: String, fields: Map<String, String>) {
        val rawMetricKey =
            listOf(name, fields.entries.sortedBy { it.key }.map { listOf(it.key, it.value) })
        val metricKey = Gson().toJson(rawMetricKey)
        val metric = Metric.newBuilder().apply {
            setName(name)
            fields.entries.forEach {
                addLabels(Label.newBuilder().setKey(it.key).setValue(it.value))
            }
        }.build()
        metricDao.create(metricKey, metric.toByteArray())
        metricDao.increment(metricKey, 1)
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        private val log = LoggerFactory.getLogger(javaClass)
        private val component = DaggerCoopComponent.builder()
            .mainCoopModule(MainCoopModule(context.app()))
            .build()

        override suspend fun doWork(): Result {
            log.info("running 10h worker")
            component.meter().upload()
            return Result.success()
        }
    }

    fun enqueuePeriodicUpload() {
        val workRequest = PeriodicWorkRequestBuilder<Worker>(Duration.ofHours(10))
            .setInitialDelay(Duration.ofHours(10))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresCharging(true)
                    .build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            "bifrost-upload-10h", ExistingPeriodicWorkPolicy.UPDATE, workRequest
        )
    }

    fun uploadLoop() {
        while (true) {
            try {
                if (isUnmetered()) upload()
            } catch (e: InterruptedException) {
                throw e
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                log.error("upload failed", e)
            }
            Thread.sleep(60_000)
        }
    }

    fun upload() {
        val androidId =
            "android_advertising_id/" + AdvertisingIdClient.getAdvertisingIdInfo(context).id
        db.runInTransaction {
            val metrics = metricDao.dump()
                .filter { it.value > 0 }
                .map { metric ->
                    Metric
                        .newBuilder(Metric.parseFrom(metric.spec))
                        .setValue(metric.value)
                        .build()
                }
            if (metrics.isEmpty()) {
                return@runInTransaction
            }
            metricDao.reset()
            val request = SendMetricsRequest.newBuilder()
                .setClientId(androidId)
                .addAllMetrics(metrics)
                .build()
            sendMetricRequestDao.add(request.toByteArray())
        }
        while (true) {
            val request = sendMetricRequestDao.next() ?: break
            val protoRequest = SendMetricsRequest
                .newBuilder(SendMetricsRequest.parseFrom(request.request))
                .setVersion(request.id)
                .build()
            log.info("send ${request.id}: ${request.request.size} bytes")
            bifrost.sendMetrics(protoRequest)
            sendMetricRequestDao.delete(request.id)
        }
    }

    private fun isUnmetered(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return true
        return networkCapabilities.hasCapability(NET_CAPABILITY_NOT_METERED)
    }
}
