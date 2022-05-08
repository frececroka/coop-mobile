package de.lorenzgorse.coopmobile.ui.overview

import android.Manifest.permission.CALL_PHONE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import de.lorenzgorse.coopmobile.PermissionRequest
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.ui.AlertDialogBuilder
import de.lorenzgorse.coopmobile.ui.AlertDialogChoice
import javax.inject.Inject

class Combox(private val fragment: Fragment) {

    private val context = fragment.requireContext()
    private val permissionRequest = PermissionRequest(fragment)

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
            if (permissionRequest.perform(CALL_PHONE)) {
                callCombox()
            } else {
                openDialer()
            }
        } else {
            // User cancelled the process in dialog.
        }
    }

    private suspend fun callCombox() {
        val result = AlertDialogBuilder(context)
            .setTitle(R.string.call_combox_title)
            .setMessage(R.string.call_combox_message)
            .setPositiveButton(R.string.yes)
            .setNegativeButton(R.string.no)
            .show()
        if (result == AlertDialogChoice.POSITIVE) {
            launchComboxWithAction(Intent.ACTION_CALL)
        }
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
