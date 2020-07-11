package de.lorenzgorse.coopmobile

import android.Manifest.permission.CALL_PHONE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class Combox(private val fragment: Fragment) {

    private val context = fragment.requireContext()

    suspend fun launch() {
        val result = ContextCompat.checkSelfPermission(context, CALL_PHONE)
        if (result == PackageManager.PERMISSION_GRANTED) {
            callCombox()
            return
        }

        val shouldShowDialog = fragment.shouldShowRequestPermissionRationale(CALL_PHONE)

        val dialog = AlertDialogBuilder(context)
            .setTitle(R.string.combox_request_title)
            .setMessage(R.string.combox_request_message)
            .setPositiveButton(R.string.okay)
            .setNegativeButton(R.string.no)

        if (!shouldShowDialog || dialog.show() == AlertDialogChoice.POSITIVE) {
            if (fragment.requestPermission(CALL_PHONE)) {
                callCombox()
            } else {
                openDialer()
            }
        } else {
            // User cancelled the process in dialog.
        }
    }

    private fun callCombox() {
        launchComboxWithAction(Intent.ACTION_CALL)
    }

    private fun openDialer() {
        launchComboxWithAction(Intent.ACTION_DIAL)
    }

    private fun launchComboxWithAction(action: String) {
        val callUri = Uri.parse("tel:+41794997979")
        val intent = Intent(action, callUri)
        context.startActivity(intent)
    }

}