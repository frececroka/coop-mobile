package de.lorenzgorse.coopmobile.fragments

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import de.lorenzgorse.coopmobile.BalanceCheckWorker
import de.lorenzgorse.coopmobile.R

class PreferencesFragment: PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        val thresholdPreference: EditTextPreference? = findPreference("check_balance_threshold")
        thresholdPreference?.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_NUMBER
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
}
