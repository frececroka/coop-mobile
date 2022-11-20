package de.lorenzgorse.coopmobile.ui.options

import android.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.client.Either
import de.lorenzgorse.coopmobile.client.Product
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.notify
import de.lorenzgorse.coopmobile.ui.AlertDialogBuilder
import de.lorenzgorse.coopmobile.ui.AlertDialogChoice
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class BuyProduct(
    private val fragment: Fragment,
    private val client: CoopClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    enum class Result {
        UserCancelled,
        NoScreenLock,
        AuthenticationFailed,
        RequestFailed,
        ResponseFailed,
        Success,
    }

    suspend fun start(product: Product): Result {
        log.info("Buying product: $product")

        if (!confirm(product)) {
            return Result.UserCancelled
        }

        when (val result = authenticate()) {
            null -> {}
            else -> return result
        }

        log.info("Sending request to buy product to Coop Mobile servers")

        val dialog = notifyBuyingInProgress(product)
        dialog.show()

        val result = client.buyProduct(product.buySpec)
        dialog.dismiss()

        return when (result) {
            is Either.Left -> {
                log.error("Failed to buy product: $result")
                notifyBuyingFailed(product)
                Result.RequestFailed
            }
            is Either.Right -> {
                log.info("Bought product: $result")
                if (result.value) {
                    notifyBuyingSuccess(product)
                    Result.Success
                } else {
                    notifyBuyingFailed(product)
                    Result.ResponseFailed
                }
            }
        }
    }

    private suspend fun confirm(product: Product): Boolean {
        val result = AlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.confirm_buy_option_title)
            .setMessage(fragment.getString(R.string.confirm_buy_option_body, product.name, product.price))
            .setNegativeButton(R.string.no)
            .setPositiveButton(R.string.yes)
            .show()
        return result == AlertDialogChoice.POSITIVE
    }

    private suspend fun authenticate(): Result? {
        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

        val biometricManager = BiometricManager.from(fragment.requireContext())
        if (biometricManager.canAuthenticate(authenticators) != BIOMETRIC_SUCCESS) {
            log.info("Not buying product, because the device is not sufficiently secured")
            fragment.notify(fragment.getString(R.string.device_not_secure))
            return Result.NoScreenLock
        }

        val prompInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(fragment.getString(R.string.confirm_buy_option_title))
            .setAllowedAuthenticators(authenticators)
            .build()

        val authenticationResult = Channel<AuthenticationResult>()
        val authenticationCallback = AuthenticationCallback(authenticationResult)
        val biometricPrompt = BiometricPrompt(fragment, authenticationCallback)
        biometricPrompt.authenticate(prompInfo)

        log.info("Waiting for authentication result")
        return when (val result = authenticationResult.receive()) {
            AuthenticationResult.Cancelled -> {
                // The user cancelled the operation,
                // so we don't do anything.
                log.info("The user cancelled the authentication")
                Result.UserCancelled
            }
            is AuthenticationResult.Error -> {
                log.error("The authentication was not successful: $result")
                // TODO: What error messages will we show? Can we be more helpful?
                fragment.notify(result.errorMessage)
                Result.AuthenticationFailed
            }
            AuthenticationResult.Success -> {
                log.info("The authentication was successful")
                null
            }
        }
    }

    private fun notifyBuyingInProgress(product: Product) =
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.buying_option)
            .setMessage(product.name)
            .setView(R.layout.loading)
            .setCancelable(false)
            .create()

    private fun notifyBuyingSuccess(product: Product) {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.option_bought_title)
            .setMessage(fragment.getString(R.string.option_bought_body, product.name))
            .setNeutralButton(R.string.okay, null)
            .create().show()
    }

    private fun notifyBuyingFailed(product: Product) {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.buying_option_failed_title)
            .setMessage(fragment.getString(R.string.buying_option_failed_body, product.name))
            .setNeutralButton(R.string.okay, null)
            .create().show()
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
