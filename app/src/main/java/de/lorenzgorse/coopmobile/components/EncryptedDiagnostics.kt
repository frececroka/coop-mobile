package de.lorenzgorse.coopmobile.components

import android.annotation.SuppressLint
import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storageMetadata
import de.lorenzgorse.coopmobile.BuildConfig
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.encryption.encrypt
import de.lorenzgorse.coopmobile.encryption.readPublicKeyRing
import de.lorenzgorse.coopmobile.userPseudoId
import de.lorenzgorse.coopmobile.waitForTask
import java.text.SimpleDateFormat
import java.util.*

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
        val userId = userPseudoId()
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format(Date())
        val ref = storage.getReference("diagnostics")
            .child(userId ?: "unknown_user")
            .child(date)
        val metadata = storageMetadata {
            setCustomMetadata("app_version", BuildConfig.VERSION_CODE.toString())
            setCustomMetadata("user_id", userId)
        }
        return try {
            waitForTask(ref.putBytes(content.toByteArray(Charsets.UTF_8)))
            waitForTask(ref.updateMetadata(metadata))
            true
        } catch (e: Exception) {
            false
        }
    }

}
