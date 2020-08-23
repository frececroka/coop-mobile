package de.lorenzgorse.coopmobile

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.CoopModule.firstInstallTimeProvider
import kotlinx.android.synthetic.main.banner.view.*


class BannerView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {

    private val analytics = FirebaseAnalytics.getInstance(context)

    init {
        inflate(context, R.layout.banner, this)
        btOkay.setOnClickListener {
            analytics.logEvent("rating_banner_okay", null)
            context.openPlayStore(); dismiss()
        }
        btNo.setOnClickListener {
            analytics.logEvent("rating_banner_no", null)
            dismiss()
        }
    }

    fun onLoadSuccess() {
        if (incrementAndGetLoadCount() >= 10 && daysSinceInstall() > 5 && !dismissed()) {
            analytics.logEvent("rating_banner_show", null)
            visibility = View.VISIBLE
        }
    }

    private fun dismiss() {
        context.getCoopSharedPreferences().edit()
            .putBoolean("rating_banner_dismissed", true)
            .apply()
        visibility = View.GONE
    }

    private fun dismissed(): Boolean {
        return context.getCoopSharedPreferences()
            .getBoolean("rating_banner_dismissed", false)
    }

    private fun daysSinceInstall(): Double {
        val elapsedMs = System.currentTimeMillis() - firstInstallTimeProvider.get(context)
        return elapsedMs.toDouble() / 1000 / 60 / 60 / 24
    }

    private fun incrementAndGetLoadCount(): Int {
        val prefs = context.getCoopSharedPreferences()
        val loadCount = prefs.getInt("load_count", 0)
        prefs.edit().putInt("load_count", loadCount + 1).apply()
        return loadCount + 1
    }

}
