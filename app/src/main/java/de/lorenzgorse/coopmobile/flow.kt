package de.lorenzgorse.coopmobile

import android.view.View
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@ExperimentalCoroutinesApi
fun View.onClickFlow(): Flow<Unit> = callbackFlow {
    setOnClickListener { trySendBlocking(Unit) }
    awaitClose { setOnClickListener(null) }
}
