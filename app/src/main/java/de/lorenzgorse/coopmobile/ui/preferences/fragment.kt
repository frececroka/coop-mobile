package de.lorenzgorse.coopmobile.ui.preferences

import android.os.Bundle
import android.text.InputType
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import de.lorenzgorse.coopmobile.*
import de.lorenzgorse.coopmobile.ui.debug.DebugMode
import de.lorenzgorse.coopmobile.ui.overview.OpenSource
import kotlinx.coroutines.launch
import javax.inject.Inject

class PreferencesFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var analytics: FirebaseAnalytics

    private val knockKnock = KnockKnock.default(500, 5000)
    private val passcode = listOf(7, 2, 5, 3)
    lateinit var openSource: OpenSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coopComponent().inject(this)
        openSource = OpenSource(requireContext(), analytics)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        val thresholdPreference: EditTextPreference? = findPreference("check_balance_threshold")
        thresholdPreference?.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val appInfo = findPreference<Preference>("app_info")
        appInfo?.summary = getString(R.string.summary_app_info, BuildConfig.VERSION_NAME)
        appInfo?.setOnPreferenceClickListener {
            onAppInfoClick(); true
        }
        val preferenceOpenSource = findPreference<Preference>("open_source")
        preferenceOpenSource?.setOnPreferenceClickListener {
            lifecycleScope.launch { openSource.launch() }; true
        }
    }

    override fun onStart() {
        super.onStart()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener { preferences, key ->
            if (key == "check_balance") {
                if (preferences.getBoolean(key, false)) {
                    BalanceCheckWorker.enqueue(requireContext())
                } else {
                    BalanceCheckWorker.cancel(requireContext())
                }
            }
        }
    }

    private fun onAppInfoClick() {
        knockKnock.knock()
        if (knockKnock.getKnocks() == passcode) {
            findPreference<Preference>("app_info")?.summary = "Welcome home, Developer."
            DebugMode.enable(requireContext())
        }
    }

}
