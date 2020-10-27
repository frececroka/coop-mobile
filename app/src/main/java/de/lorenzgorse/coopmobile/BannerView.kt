package de.lorenzgorse.coopmobile

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.CoopModule.firstInstallTimeProvider
import kotlinx.android.synthetic.main.banner.view.*


class BannerView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {

    private val analytics = FirebaseAnalytics.getInstance(context)

    private val dismissedFuse = Fuse(context, "rating_banner_dismissed")

    var activity: Activity? = null

    init {
        inflate(context, R.layout.banner, this)
        btOkay.setOnClickListener {
            analytics.logEvent("RatingBanner_Okay", null)
            startReview()
        }
        btNo.setOnClickListener {
            analytics.logEvent("RatingBanner_No", null)
            dismiss()
        }
        // ↓↓ This is here to be backwards compatible. Remove after ca 10 weeks. ↓↓
        if (dismissed()) dismissedFuse.burn()
        // ↑↑ This is here to be backwards compatible. Remove after ca 10 weeks. ↑↑
    }

    fun onLoadSuccess() {
        if (incrementAndGetLoadCount() >= 10 && daysSinceInstall() > 5 && !dismissed()) {
            analytics.logEvent("RatingBanner_Show", null)
            visibility = View.VISIBLE
        }
    }

    fun startReview() {
        layResponse.visibility = View.GONE
        layProgress.visibility = View.VISIBLE
        val activity = activity
        if (activity != null) {
            val reviewManager = ReviewManagerFactory.create(context)
            val request = reviewManager.requestReviewFlow()
            request.addOnCompleteListener {
                dismiss()
                if (it.isSuccessful) {
                    analytics.logEvent("RatingBanner_InAppReview", null)
                    reviewManager.launchReviewFlow(activity, it.result)
                } else {
                    analytics.logEvent("RatingBanner_ExternalPlayStore2", null)
                    context.openPlayStore()
                }
            }
        } else {
            analytics.logEvent("RatingBanner_ExternalPlayStore1", null)
            dismiss()
            context.openPlayStore()
        }
    }

    private fun dismiss() {
        visibility = View.GONE
        dismissedFuse.burn()
    }

    private fun dismissed(): Boolean {
        // ↓↓ This is here to be backwards compatible. Remove after ca 10 weeks. ↓↓
        val legacyDismissed = context.getCoopSharedPreferences()
            .getBoolean("rating_banner_dismissed", false)
        // ↑↑ This is here to be backwards compatible. Remove after ca 10 weeks. ↑↑
        return dismissedFuse.isBurnt() || legacyDismissed
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
