package de.lorenzgorse.coopmobile.components

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storageMetadata
import de.lorenzgorse.coopmobile.BuildConfig
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.encryption.encrypt
import de.lorenzgorse.coopmobile.encryption.readPublicKeyRing
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class EncryptedDiagnostics(private val context: Context) {

    private val analytics = FirebaseAnalytics.getInstance(context)
    private val storage = FirebaseStorage.getInstance()

    suspend fun send(message: String): Boolean {
        analytics.logEvent("Diagnostics_Begin", null)
        val publicKeyRingStream = context.resources.openRawResource(R.raw.publickey)
        val publicKey = readPublicKeyRing(publicKeyRingStream).publicKey
        val encryptedMessage = encrypt(publicKey, message)
        val successful = upload(encryptedMessage)
        analytics.logEvent(
            if (successful) "Diagnostics_Success"
            else "Diagnostics_Fail",
            null)
        return successful
    }

    @SuppressLint("SimpleDateFormat")
    private suspend fun upload(content: String): Boolean {
        val userId = try { wait(analytics.appInstanceId) } catch (e: Exception) { null }
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format(Date())
        val ref = storage.getReference("diagnostics")
            .child(userId ?: "unknown_user")
            .child(date)
        val metadata = storageMetadata {
            setCustomMetadata("app_version", BuildConfig.VERSION_CODE.toString())
            setCustomMetadata("user_id", userId)
        }
        return try {
            wait(ref.putBytes(content.toByteArray(Charsets.UTF_8)))
            wait(ref.updateMetadata(metadata))
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun <T> wait(task: Task<T>) : T {
        return suspendCoroutine {
            task.addOnCompleteListener { task ->
                val ex = task.exception
                if (ex != null) {
                    it.resumeWithException(ex)
                } else {
                    it.resume(task.result)
                }
            }
        }
    }

}
