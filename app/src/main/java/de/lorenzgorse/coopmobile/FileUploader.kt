package de.lorenzgorse.coopmobile

import android.content.Context
import androidx.work.*
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import de.lorenzgorse.coopmobile.FileUploader.UploadWorker.Companion.BYTES
import de.lorenzgorse.coopmobile.FileUploader.UploadWorker.Companion.PATH
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

class FileUploader(context: Context) {
    companion object {
        val log = LoggerFactory.getLogger(FileUploader::class.java)
    }

    private val workManager = WorkManager.getInstance(context)

    fun upload(path: String, bytes: ByteArray) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(PATH to path, BYTES to bytes))
            .build()
        workManager.enqueue(workRequest)
    }

    class UploadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        companion object {
            const val PATH = "path"
            const val BYTES = "bytes"
        }

        override fun doWork(): Result {
            val path = inputData.getString(PATH) ?: return Result.failure()
            val bytes = inputData.getByteArray(BYTES) ?: return Result.failure()
            return runBlocking { doWork(path, bytes) }
        }

        private suspend fun doWork(path: String, bytes: ByteArray): Result {
            val storageReference = Firebase.storage.reference.child(path)
            try {
                waitForTask(storageReference.putBytes(bytes))
                return Result.success()
            } catch (e: Exception) {
                log.error("Upload of $path failed", e)
                return Result.retry()
            }
        }
    }
}
