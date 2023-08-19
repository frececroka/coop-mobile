package de.lorenzgorse.coopmobile.ui.overview

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.CoopModule.firstInstallTimeProvider
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.components.Counter
import de.lorenzgorse.coopmobile.components.Fuse
import de.lorenzgorse.coopmobile.databinding.BannerBinding
import de.lorenzgorse.coopmobile.openPlayStore


class BannerView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {

    private val analytics = FirebaseAnalytics.getInstance(context)

    private val dismissedFuse = Fuse(context, "rating_banner_dismissed")
    private val loadCount = Counter(context, "load_count")

    private val binding: BannerBinding

    var activity: Activity? = null

    init {
        binding = BannerBinding.bind(inflate(context, R.layout.banner, this))
        binding.btOkay.setOnClickListener {
            analytics.logEvent("RatingBanner_Interaction", bundleOf("Choice" to "Okay"))
            startReview()
        }
        binding.btNo.setOnClickListener {
            analytics.logEvent("RatingBanner_Interaction", bundleOf("Choice" to "No"))
            dismiss()
        }
    }

    fun onLoadSuccess() {
        loadCount.increment()
        if (loadCount.get() >= 10 && daysSinceInstall() > 5 && !dismissed()) {
            analytics.logEvent("RatingBanner_Show", null)
            visibility = View.VISIBLE
        }
    }

    private fun startReview() {
        binding.layResponse.visibility = View.GONE
        binding.layProgress.visibility = View.VISIBLE
        val activity = activity
        if (activity != null) {
            val reviewManager = ReviewManagerFactory.create(context)
            val request = reviewManager.requestReviewFlow()
            request.addOnCompleteListener {
                dismiss()
                if (it.isSuccessful) {
                    analytics.logEvent(
                        "RatingBanner_Review",
                        bundleOf("Method" to "InAppReview")
                    )
                    reviewManager.launchReviewFlow(activity, it.result)
                } else {
                    analytics.logEvent(
                        "RatingBanner_Review",
                        bundleOf("Method" to "ExternalPlayStore2")
                    )
                    context.openPlayStore()
                }
            }
        } else {
            analytics.logEvent(
                "RatingBanner_Review",
                bundleOf("Method" to "ExternalPlayStore1")
            )
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

}
