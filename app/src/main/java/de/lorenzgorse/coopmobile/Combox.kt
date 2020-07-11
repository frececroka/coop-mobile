package de.lorenzgorse.coopmobile

import android.Manifest.permission.CALL_PHONE
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class Combox(private val fragment: Fragment) {

    private val context = fragment.requireContext()

    fun launch() {
        val result = ContextCompat.checkSelfPermission(context, CALL_PHONE)
        if (result == PackageManager.PERMISSION_GRANTED) {
            callCombox()
            return
        }

        if (fragment.shouldShowRequestPermissionRationale(CALL_PHONE)) {
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.combox_request_title))
                .setMessage(context.getString(R.string.combox_request_message))
                .setNeutralButton(R.string.okay) { _, _ ->
                    requestComboxPermission()
                }
                .show()
        } else {
            requestComboxPermission()
        }
    }

    private fun requestComboxPermission() {
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                callCombox()
            } else {
                openDialer()
            }
        }.launch(CALL_PHONE)
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
