package de.lorenzgorse.coopmobile.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.*

class BuyProductFragment : Fragment() {

    private lateinit var inflater: LayoutInflater
    private lateinit var analytics: FirebaseAnalytics

    private lateinit var productBuySpec: ProductBuySpec

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics = createAnalytics(requireContext())
        inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        productBuySpec = arguments?.get("product") as ProductBuySpec
        authenticate()
    }

    override fun onStart() {
        super.onStart()
        analytics.setCurrentScreen(requireActivity(), "BuyProduct", null)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_buy_product, container, false)
    }

    private fun authenticate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            BiometricPrompt.Builder(context)
                .setTitle("Kauf bestätigen")
                .setDeviceCredentialAllowed(true)
                .build()
                .authenticate(CancellationSignal(), requireContext().mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL) {
                            deviceNotSecure()
                        } else {
                            authentificationFailed(errString)
                        }
                    }
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        buyProduct()
                    }
                    override fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence?) {
                        if (helpString != null) {
                            Snackbar.make(view!!, helpString, 5000).show()
                        }
                    }
                    override fun onAuthenticationFailed() {
                        authentificationFailed()
                    }
                })
        } else {
            val keyguardManager = requireContext().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val intent = keyguardManager.createConfirmDeviceCredentialIntent("Kauf bestätigen", null)
            if (intent != null) {
                startActivityForResult(intent, 0)
            } else {
                deviceNotSecure()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            buyProduct()
        } else {
            authentificationFailed()
        }
    }

    private fun deviceNotSecure() {
        Log.i("CoopMobile", "Device is not secure.")
        Snackbar.make(view!!, "Das Gerät muss gesichert sein (PIN-Code, Passwort, Fingerabdruck, ...), um Optionen kaufen zu können.", 5000).show()
        findNavController().popBackStack()
    }

    private fun authentificationFailed(errString: CharSequence? = null) {
        Log.i("CoopMobile", errString.toString())
        val nonNullErrString = errString ?: "Authentifizierung war nicht erfolgreich."
        Snackbar.make(view!!, nonNullErrString, 5000).show()
        findNavController().popBackStack()
    }

    private fun buyProduct() {
//        BuyProduct(productBuySpec).execute()
        Snackbar.make(view!!, R.string.buy_option_not_available, 5000).show()
        findNavController().popBackStack()
    }

    @SuppressLint("StaticFieldLeak")
    inner class BuyProduct(private val buySpec: ProductBuySpec) : LoadDataAsyncTask<Void, Boolean>(requireContext()) {

        override fun loadData(client: CoopClient): Boolean {
            return client.buyProduct(buySpec)
        }

        override fun onSuccess(result: Boolean) {
            findNavController().popBackStack()
            if (result) {
                Snackbar.make(view!!, R.string.bought, 5000).show()
            } else {
                Snackbar.make(view!!, R.string.buying_failed, 5000).show()
            }
        }

        override fun onFailure(error: LoadDataError) {
            findNavController().popBackStack()
            Snackbar.make(view!!, R.string.buying_failed, 5000).show()
        }

    }

}
