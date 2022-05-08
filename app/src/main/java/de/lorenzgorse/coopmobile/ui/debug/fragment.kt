package de.lorenzgorse.coopmobile.ui.debug

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.coopComponent
import de.lorenzgorse.coopmobile.preferences.getCoopSharedPreferences
import kotlinx.android.synthetic.main.fragment_debug.*
import java.text.SimpleDateFormat
import java.util.*


class DebugFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coopComponent().inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_debug, container, false)
    }

    override fun onStart() {
        super.onStart()
        renderGeneralAppInformation()
        renderPreferences()
    }

    private fun renderGeneralAppInformation() {
        val packageInfo = try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return
        }
        addAppInfo("Version", packageInfo.versionName)
        addAppInfo("Install Location", packageInfo.installLocation.toString())
        addAppInfoTimestamp("First Install Time", packageInfo.firstInstallTime)
        addAppInfoTimestamp("Last Update Time", packageInfo.lastUpdateTime)
    }

    private fun addAppInfoTimestamp(key: String, value: Long) {
        addAppInfo(key, SimpleDateFormat.getDateTimeInstance().format(Date(value)))
    }

    private fun addAppInfo(key: String, value: String) {
        createEntryView(layGeneral, key, value)
    }

    private fun renderPreferences() {
        laySharedPrefs.removeAllViews()
        val prefs = requireContext().getCoopSharedPreferences()
        for (entry in prefs.all) {
            val value = if (entry.key == "password") "**********" else entry.value.toString()
            createEntryView(laySharedPrefs, entry.key, value) {
                prefs.edit().remove(entry.key).apply()
                renderPreferences()
            }
        }
    }

    private fun createEntryView(
        parent: ViewGroup,
        key: String,
        value: String,
        onClick: (() -> Unit)? = null
    ) {
        val entryLayout = layoutInflater.inflate(R.layout.debug_entry, parent, false)

        entryLayout.findViewById<TextView>(R.id.txtKey).text = key
        entryLayout.findViewById<TextView>(R.id.txtValue).text = value

        entryLayout.setOnClickListener {
            onClick?.let { it() }
        }

        parent.addView(entryLayout)
    }

}
