package de.lorenzgorse.coopmobile.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation.findNavController
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.components.EncryptedDiagnostics
import de.lorenzgorse.coopmobile.data.*
import de.lorenzgorse.coopmobile.openPlayStore
import de.lorenzgorse.coopmobile.ui.debug.DebugMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
class RemoteDataView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {

    companion object {
        @ExperimentalCoroutinesApi
        fun inflate(
            inflater: LayoutInflater,
            container: ViewGroup?,
            contentViewId: Int
        ): RemoteDataView {
            val remoteDataView = inflater.inflate(R.layout.fragment_remote_data, container, false) as RemoteDataView
            val contentView = remoteDataView.contentView
            val dataView = inflater.inflate(contentViewId, contentView, false)
            contentView.addView(dataView)
            return remoteDataView
        }
    }

    private var bound = false

    val contentView: LinearLayout
    private val loadingView: LoadingView
    private val errorView: View
    private val errorNoNetworkView: View
    private val errorPlanUnsupportedView: View
    private val errorUpdateView: View
    private val errorOtherView: View
    private val errorOtherMessageView: TextView
    private val sendDiagnosticsButton: Button

    private val encryptedDiagnostics = EncryptedDiagnostics(context)

    init {
        inflate(context, R.layout.remote_data, this)
        contentView = findViewById(R.id.rdContent)
        loadingView = findViewById(R.id.rdLoading)
        errorView = findViewById(R.id.rdError)
        errorNoNetworkView = findViewById(R.id.rdErrorNoNetwork)
        errorPlanUnsupportedView = findViewById(R.id.rdErrorPlanUnsupported)
        errorUpdateView = findViewById(R.id.rdErrorUpdate)
        errorOtherView = findViewById(R.id.rdErrorOther)
        errorOtherMessageView = findViewById(R.id.rdErrorOtherMessage)
        sendDiagnosticsButton = findViewById(R.id.rdSendDiagnostics)

        findViewById<Button>(R.id.btGoToPlayStore)
            .setOnClickListener { context.openPlayStore() }
    }

    fun <T> bindState(state: Flow<State<T, CoopError>>) {
        if (bound) throw IllegalStateException()
        else bound = true

        val lifecycleOwner = findViewTreeLifecycleOwner()!!

        val data = state.data()
        lifecycleOwner.applyVisibility(data.map { it != null }, contentView)

        val loading = state.loadingState()
        lifecycleOwner.applyVisibility(loading, loadingView)

        val loadingIndefinite = state.loadingStateIndefinite()
        lifecycleOwner.onEach(loadingIndefinite.filter { it }) {
            loadingView.makeIndeterminate()
        }

        val loadingDefinite = state.loadingStateDefinite()
        lifecycleOwner.onEach(loadingDefinite.filterNotNull()) { (k, n) ->
            loadingView.setProgress(k, n)
        }

        // TODO: Use a when block for the handling to notice when we forget variants?
        val error = state.error()
        lifecycleOwner.applyVisibility(error.map { it != null }, errorView)
        lifecycleOwner.applyVisibility(error.map { it is CoopError.NoNetwork }, errorNoNetworkView)
        lifecycleOwner.applyVisibility(error.map { it is CoopError.PlanUnsupported }, errorPlanUnsupportedView)
        lifecycleOwner.applyVisibility(error.map { it is CoopError.HtmlChanged }, errorUpdateView)
        lifecycleOwner.applyVisibility(error.map { it is CoopError.Other }, errorOtherView)
        lifecycleOwner.onEach(error.filterIsInstance<CoopError.Other>()) {
            errorOtherMessageView.text = it.message
        }

        lifecycleOwner.lifecycleScope.launch {
            error
                .filter { it is CoopError.FailedLogin || it is CoopError.Unauthorized || it is CoopError.NoClient }
                .collect {
                    findNavController(this@RemoteDataView).navigate(R.id.action_login)
                }
        }

        val faultyDocument = error.filterIsInstance<CoopError.HtmlChanged>()
            .map { it.ex.document }

        val diagnosticsEnabled = faultyDocument.map { it != null && DebugMode.isEnabled(context) }
        lifecycleOwner.applyVisibility(diagnosticsEnabled, sendDiagnosticsButton)

        val uploadDiagnostics = faultyDocument.filterNotNull().flatMapLatest { document ->
            sendDiagnosticsButton.onClickFlow().map { document }
        }

        lifecycleOwner.onEach(uploadDiagnostics) { document ->
            sendDiagnosticsButton.isEnabled = false
            sendDiagnosticsButton.text = context.getString(R.string.diagnostics_uploading)
            val isSuccess = encryptedDiagnostics.send(document.outerHtml())
            sendDiagnosticsButton.text = context.getString(
                if (isSuccess) R.string.diagnostics_upload_successful
                else R.string.diagnostics_upload_failed
            )
        }
    }

}

fun LifecycleOwner.applyVisibility(isVisible: Flow<Boolean>, view: View) {
    lifecycleScope.launch {
        isVisible.collect {
            view.visibility = if (it) View.VISIBLE else View.GONE
        }
    }
}

fun <T> LifecycleOwner.onEach(values: Flow<T>, action: suspend (T) -> Unit) {
    lifecycleScope.launch {
        values.collect { action(it) }
    }
}
