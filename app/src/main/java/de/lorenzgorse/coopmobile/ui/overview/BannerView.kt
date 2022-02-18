package de.lorenzgorse.coopmobile.ui.overview

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.CoopModule.firstInstallTimeProvider
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.components.Counter
import de.lorenzgorse.coopmobile.components.Fuse
import de.lorenzgorse.coopmobile.openPlayStore
import de.lorenzgorse.coopmobile.preferences.getCoopSharedPreferences
import kotlinx.android.synthetic.main.banner.view.*
import kotlin.math.max


class BannerView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {

    private val analytics = FirebaseAnalytics.getInstance(context)

    private val dismissedFuse = Fuse(context, "rating_banner_dismissed")
    private val loadCount = Counter(context, "load_count")

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
    }

    fun onLoadSuccess() {
        if (incrementAndGetLoadCount() >= 10 && daysSinceInstall() > 5 && !dismissed()) {
            analytics.logEvent("RatingBanner_Show", null)
            visibility = View.VISIBLE
        }
    }

    private fun startReview() {
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
        return dismissedFuse.isBurnt()
    }

    private fun daysSinceInstall(): Double {
        val elapsedMs = System.currentTimeMillis() - firstInstallTimeProvider.get(context)
        return elapsedMs.toDouble() / 1000 / 60 / 60 / 24
    }

    private fun incrementAndGetLoadCount(): Int {
        loadCount.increment()
        return loadCount.get()
    }

}
