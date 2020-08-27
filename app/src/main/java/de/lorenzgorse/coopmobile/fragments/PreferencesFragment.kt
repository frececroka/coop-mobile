package de.lorenzgorse.coopmobile.fragments

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import de.lorenzgorse.coopmobile.BalanceCheckWorker
import de.lorenzgorse.coopmobile.BuildConfig
import de.lorenzgorse.coopmobile.KnockKnock
import de.lorenzgorse.coopmobile.R

class PreferencesFragment: PreferenceFragmentCompat() {

    private val knockKnock = KnockKnock.default(500, 5000)
    private val passcode = listOf(7, 2, 5, 3)

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
    }

    override fun onStart() {
        super.onStart()
        val sharedPreferences = preferenceManager.sharedPreferences
        sharedPreferences.registerOnSharedPreferenceChangeListener { preferences, key ->
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
            DebugFragment.enable(requireContext())
        }
    }

}
