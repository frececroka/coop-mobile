package de.lorenzgorse.coopmobile.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.Either
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

@FlowPreview
@ExperimentalCoroutinesApi
abstract class CoopViewModel(app: Application) : AndroidViewModel(app) {

    private val _refresh = MutableSharedFlow<Unit>(replay = 1)
    private val refresh: Flow<Unit> = _refresh

    init {
        viewModelScope.launch { refresh() }
    }

    suspend fun refresh() {
        _refresh.emit(Unit)
    }

    protected fun <T> load(op: suspend () -> Either<CoopError, T>) =
        stateFlow(refresh, op)

    protected fun <T> loadNow(op: suspend () -> Either<CoopError, T>) =
        stateFlow(flowOf(Unit), op)

    protected fun <T> Flow<T>.share() = shareIn(viewModelScope, Eagerly, replay = 1)

}
