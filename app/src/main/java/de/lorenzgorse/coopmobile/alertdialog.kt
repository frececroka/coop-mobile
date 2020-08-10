package de.lorenzgorse.coopmobile

import android.app.AlertDialog
import android.content.Context
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.widget.TextView
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class AlertDialogChoice {
    POSITIVE, NEGATIVE, NEUTRAL, DISMISSED
}

class AlertDialogBuilder(context: Context) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val layoutInflater = LayoutInflater.from(context)

    private val builder = AlertDialog.Builder(context)
    private var positiveButton: Int? = null
    private var negativeButton: Int? = null
    private var neutralButton: Int? = null

    fun setTitle(textId: Int): AlertDialogBuilder {
        builder.setTitle(textId)
        return this
    }

    fun setMessage(textId: Int): AlertDialogBuilder {
        builder.setMessage(textId)
        return this
    }

    fun setMessage(string: String): AlertDialogBuilder {
        builder.setMessage(string)
        return this
    }

    fun setHtml(content: Spanned): AlertDialogBuilder {
        val layout = layoutInflater.inflate(R.layout.alert_dialog, null)
        val textView = layout.findViewById<TextView>(R.id.message)
        textView.text = content
        textView.movementMethod = LinkMovementMethod.getInstance()
        builder.setView(layout)
        return this
    }

    fun setPositiveButton(textId: Int): AlertDialogBuilder {
        positiveButton = textId
        return this
    }

    fun setNegativeButton(textId: Int): AlertDialogBuilder {
        negativeButton = textId
        return this
    }

    fun setNeutralButton(textId: Int): AlertDialogBuilder {
        neutralButton = textId
        return this
    }

    suspend fun show(): AlertDialogChoice = suspendCoroutine { cont ->
        log.info("Showing AlertDialog")

        val resumed = AtomicBoolean(false)
        fun resume(result: AlertDialogChoice) {
            val shouldResume = resumed.compareAndSet(false, true)
            log.info("Dialog result is $result, shouldResume=$shouldResume")
            if (shouldResume) cont.resume(result)
        }

        positiveButton?.let { builder.setPositiveButton(it) { _, _ ->
            resume(AlertDialogChoice.POSITIVE) } }
        negativeButton?.let { builder.setNegativeButton(it) { _, _ ->
            resume(AlertDialogChoice.NEGATIVE) } }
        neutralButton?.let { builder.setNeutralButton(it) { _, _ ->
            resume(AlertDialogChoice.NEUTRAL) } }

        builder.setOnDismissListener {
            resume(AlertDialogChoice.DISMISSED) }

        builder.show()
    }

}
