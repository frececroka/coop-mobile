package de.lorenzgorse.coopmobile.ui.buyproduct

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.*
import de.lorenzgorse.coopmobile.client.Either
import de.lorenzgorse.coopmobile.client.ProductBuySpec
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class BuyProductFragment : Fragment() {

    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var inflater: LayoutInflater
    private lateinit var analytics: FirebaseAnalytics

    private lateinit var client: CoopClient

    private lateinit var productBuySpec: ProductBuySpec

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics = createAnalytics(requireContext())
        inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        client = createClient(requireContext())
        productBuySpec = arguments?.get("product") as ProductBuySpec
        authenticate()
    }

    override fun onStart() {
        super.onStart()
        analytics.setScreen("BuyProduct")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_buy_product, container, false)
    }

    private fun authenticate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL) {
                        deviceNotSecure()
                    } else {
                        authentificationFailed(errString)
                    }
                }

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult?
                ) {
                    lifecycleScope.launch { buyProduct() }
                }

                override fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence?) {
                    if (helpString != null) {
                        notify(helpString)
                    }
                }

                override fun onAuthenticationFailed() {
                    authentificationFailed()
                }
            }

            BiometricPrompt.Builder(context)
                .setTitle(getString(R.string.confirm_purchase))
                .setDeviceCredentialAllowed(true)
                .build()
                .authenticate(CancellationSignal(), requireContext().mainExecutor, callback)
        } else {
            val keyguardManager = requireContext().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            @Suppress("DEPRECATION")
            val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                getString(R.string.confirm_purchase), null)
            if (intent != null) {
                registerForActivityResult(object : ActivityResultContract<Int, Int>() {
                    override fun createIntent(context: Context, input: Int) = intent
                    override fun parseResult(resultCode: Int, intent: Intent?) = resultCode
                }) {
                    if (it == Activity.RESULT_OK) {
                        lifecycleScope.launch { buyProduct() }
                    } else {
                        authentificationFailed()
                    }
                }
            } else {
                deviceNotSecure()
            }
        }
    }

    private fun deviceNotSecure() {
        log.info("Device is not secure.")
        notify(getString(R.string.device_not_secure))
        findNavController().popBackStack()
    }

    private fun authentificationFailed(errString: CharSequence? = null) {
        log.info(errString.toString())
        val nonNullErrString = errString ?: getString(R.string.authentication_unsuccessful)
        notify(nonNullErrString)
        findNavController().popBackStack()
    }

    @Suppress("UNREACHABLE_CODE")
    private suspend fun buyProduct() {
        notify(R.string.buy_option_not_available)
        findNavController().popBackStack()

        return

        when (val result = client.buyProduct(productBuySpec)) {
            is Either.Left -> {
                findNavController().popBackStack()
                notify(R.string.buying_failed)
            }
            is Either.Right -> {
                findNavController().popBackStack()
                if (result.value) {
                    notify(R.string.bought)
                } else {
                    notify(R.string.buying_failed)
                }
            }
        }
    }

}
