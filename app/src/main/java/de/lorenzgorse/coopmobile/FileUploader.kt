package de.lorenzgorse.coopmobile

import android.content.Context
import androidx.work.*
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import de.lorenzgorse.coopmobile.FileUploader.UploadWorker.Companion.PATH
import de.lorenzgorse.coopmobile.FileUploader.UploadWorker.Companion.PAYLOAD_PATH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.*

class FileUploader(context: Context) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(FileUploader::class.java)
    }

    private val cacheDir = context.filesDir.resolve("FileUploader")
    private val workManager = WorkManager.getInstance(context)

    init {
        cacheDir.mkdir()
        cacheDir.listFiles()!!
            .filter {
                val lastModifed = Instant.ofEpochMilli(it.lastModified())
                val cutoff = Instant.now().minus(Duration.ofDays(1))
                lastModifed.isBefore(cutoff)
            }
            .forEach { it.delete() }
    }

    fun upload(path: String, payload: ByteArray) {
        val payloadFile = cacheDir.resolve(UUID.randomUUID().toString())
        payloadFile.writeBytes(payload)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(PATH to path, PAYLOAD_PATH to payloadFile.absolutePath))
            .build()
        workManager.enqueue(workRequest)
    }

    class UploadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        companion object {
            const val PATH = "path"
            const val PAYLOAD_PATH = "payload_path"
        }

        override fun doWork(): Result {
            val path = inputData.getString(PATH) ?: return Result.failure()
            val payloadPath = inputData.getString(PAYLOAD_PATH) ?: return Result.failure()
            val payloadFile = Paths.get(payloadPath).toFile()
            return runBlocking { doWork(path, payloadFile) }
        }

        private suspend fun doWork(path: String, payloadFile: File): Result {
            val payload = withContext(Dispatchers.IO) { payloadFile.readBytes() }
            val storageReference = Firebase.storage.reference.child(path)
            return try {
                waitForTask(storageReference.putBytes(payload))
                Result.success()
            } catch (e: Exception) {
                log.error("Upload of $path failed", e)
                Result.retry()
            }
        }
    }
}
