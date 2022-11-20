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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class BuyProduct(
    private val fragment: Fragment,
    private val client: CoopClient
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun start(spec: ProductBuySpec) {
        log.info("Buying product: $spec")

        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

        val biometricManager = BiometricManager.from(fragment.requireContext())
        if (biometricManager.canAuthenticate(authenticators) != BIOMETRIC_SUCCESS) {
            log.info("Not buying product, because the device is not sufficiently secured")
            fragment.notify(fragment.getString(R.string.device_not_secure))
            return
        }

        val prompInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(fragment.getString(R.string.confirm_purchase))
            .setAllowedAuthenticators(authenticators)
            .build()

        val authenticationResult = Channel<AuthenticationResult>()
        val authenticationCallback = AuthenticationCallback(authenticationResult)
        val biometricPrompt = BiometricPrompt(fragment, authenticationCallback)
        biometricPrompt.authenticate(prompInfo)

        log.info("Waiting for authentication result")
        when (val result = authenticationResult.receive()) {
            AuthenticationResult.Cancelled -> {
                // The user cancelled the operation,
                // so we don't do anything.
                log.info("The user cancelled the authentication")
                return
            }
            is AuthenticationResult.Error -> {
                log.error("The authentication was not successful: $result")
                // TODO: What error messages will we show? Can we be more helpful?
                fragment.notify(result.errorMessage)
                return
            }
            AuthenticationResult.Success -> {
                log.info("The authentication was successful")
            }
        }

        log.info("Sending request to buy product to Coop Mobile servers")

        // TODO: Show progress indicator.
        when (val result = client.buyProduct(spec)) {
            is Either.Left -> {
                log.error("Failed to buy product: $result")
                fragment.notify(R.string.buying_failed)
            }
            is Either.Right -> {
                log.info("Bought product: $result")
                if (result.value) {
                    fragment.notify(R.string.bought)
                } else {
                    fragment.notify(R.string.buying_failed)
                }
            }
        }
    }

    private sealed class AuthenticationResult {
        object Success : AuthenticationResult()
        object Cancelled : AuthenticationResult()
        data class Error(val errorCode: Int, val errorMessage: String) : AuthenticationResult()
    }

    private inner class AuthenticationCallback(
        private val resultChannel: SendChannel<AuthenticationResult>,
    ) : BiometricPrompt.AuthenticationCallback() {

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            val result = when (errorCode) {
                BiometricPrompt.ERROR_USER_CANCELED -> AuthenticationResult.Cancelled
                else -> AuthenticationResult.Error(errorCode, errString.toString())
            }
            fragment.lifecycleScope.launch {
                resultChannel.send(result)
            }
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            fragment.lifecycleScope.launch {
                resultChannel.send(AuthenticationResult.Success)
            }
        }

    }

}
