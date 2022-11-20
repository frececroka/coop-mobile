package de.lorenzgorse.coopmobile.ui.options

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.client.Either
import de.lorenzgorse.coopmobile.client.ProductBuySpec
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.notify
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class BuyProduct(
    private val fragment: Fragment,
    private val client: CoopClient
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun start(spec: ProductBuySpec) {
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
                fragment.lifecycleScope.launch { buyProduct(spec) }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                authentificationFailed()
            }
        }

        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

        val biometricManager = BiometricManager.from(fragment.requireContext())
        if (biometricManager.canAuthenticate(authenticators) != BIOMETRIC_SUCCESS) {
            deviceNotSecure()
            return
        }

        val prompInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(fragment.getString(R.string.confirm_purchase))
            .setAllowedAuthenticators(authenticators)
            .build()

        val biometricPrompt = BiometricPrompt(fragment, callback)
        biometricPrompt.authenticate(prompInfo)
    }

    private fun deviceNotSecure() {
        log.info("Device is not secure.")
        fragment.notify(fragment.getString(R.string.device_not_secure))
    }

    private fun authentificationCancelled() {
    }

    private fun authentificationFailed(errString: CharSequence? = null) {
        val nonNullErrString = errString
            ?: fragment.getString(R.string.authentication_unsuccessful)
        fragment.notify(nonNullErrString)
    }

    private suspend fun buyProduct(spec: ProductBuySpec) {
        when (val result = client.buyProduct(spec)) {
            is Either.Left -> {
                fragment.notify(R.string.buying_failed)
            }
            is Either.Right -> {
                if (result.value) {
                    fragment.notify(R.string.bought)
                } else {
                    fragment.notify(R.string.buying_failed)
                }
            }
        }
    }

}
