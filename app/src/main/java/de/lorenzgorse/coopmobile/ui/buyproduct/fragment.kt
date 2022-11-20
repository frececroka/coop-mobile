package de.lorenzgorse.coopmobile.ui.buyproduct

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import de.lorenzgorse.coopmobile.FirebaseAnalytics
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.client.Either
import de.lorenzgorse.coopmobile.client.ProductBuySpec
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.coopComponent
import de.lorenzgorse.coopmobile.notify
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import javax.inject.Inject

class BuyProductFragment : Fragment() {

    private val log = LoggerFactory.getLogger(javaClass)

    @Inject lateinit var analytics: FirebaseAnalytics
    @Inject lateinit var client: CoopClient

    private lateinit var inflater: LayoutInflater

    private lateinit var productBuySpec: ProductBuySpec

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coopComponent().inject(this)
        inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
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
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                    authentificationCancelled()
                } else {
                    authentificationFailed(errString)
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                lifecycleScope.launch { buyProduct() }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                authentificationFailed()
            }
        }

        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

        val biometricManager = BiometricManager.from(requireContext())
        if (biometricManager.canAuthenticate(authenticators) != BIOMETRIC_SUCCESS) {
            deviceNotSecure()
            return
        }

        val prompInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.confirm_purchase))
            .setAllowedAuthenticators(authenticators)
            .build()

        val biometricPrompt = BiometricPrompt(this, callback)
        biometricPrompt.authenticate(prompInfo)
    }

    private fun deviceNotSecure() {
        log.info("Device is not secure.")
        notify(getString(R.string.device_not_secure))
        findNavController().popBackStack()
    }

    private fun authentificationCancelled() {
        findNavController().popBackStack()
    }

    private fun authentificationFailed(errString: CharSequence? = null) {
        val nonNullErrString = errString ?: getString(R.string.authentication_unsuccessful)
        notify(nonNullErrString)
        findNavController().popBackStack()
    }

    private suspend fun buyProduct() {
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
