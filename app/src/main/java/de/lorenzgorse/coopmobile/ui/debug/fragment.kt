package de.lorenzgorse.coopmobile.ui.debug

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import de.lorenzgorse.coopmobile.*
import de.lorenzgorse.coopmobile.databinding.FragmentDebugBinding
import de.lorenzgorse.coopmobile.preferences.getCoopSharedPreferences
import de.lorenzgorse.coopmobile.ui.onEach
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*


class DebugFragment : Fragment() {

    private lateinit var binding: FragmentDebugBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coopComponent().inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDebugBinding.inflate(inflater)
        return binding.root
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onStart() {
        super.onStart()
        renderGeneralAppInformation()
        renderPreferences()

        lifecycleScope.launch {
            val firebaseId = waitForTask(FirebaseInstallations.getInstance().id)
            addAppInfo("Firebase id", firebaseId)
        }

        viewLifecycleOwner.onEach(binding.btRefreshRemoteConfig.onClickFlow()) {
            waitForTask(Firebase.remoteConfig.fetch(0))
            notify("RemoteConfig refreshed")
        }
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
        createEntryView(binding.layGeneral, key, value)
    }

    private fun renderPreferences() {
        binding.laySharedPrefs.removeAllViews()
        val prefs = requireContext().getCoopSharedPreferences()
        for (entry in prefs.all) {
            val value = if (entry.key == "password") "**********" else entry.value.toString()
            createEntryView(binding.laySharedPrefs, entry.key, value) {
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
