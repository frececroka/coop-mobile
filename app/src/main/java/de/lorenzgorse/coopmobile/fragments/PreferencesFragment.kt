package de.lorenzgorse.coopmobile.fragments

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import de.lorenzgorse.coopmobile.R

class PreferencesFragment: PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}
