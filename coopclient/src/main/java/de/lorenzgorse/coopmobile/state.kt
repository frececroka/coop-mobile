package de.lorenzgorse.coopmobile

import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.Either
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*

sealed class State<T, E> {

    // nubmer of items already loaded
    abstract val k: Int

    // number of total items
    abstract val n: Int

    // k out of n items are loaded, the progress bar should be indefinite if definite is false
    data class Loading<T, E>(override val k: Int, override val n: Int, val definite: Boolean = false) : State<T, E>()

    // all n items are loaded, where d is the loaded data
    data class Loaded<T, E>(val d: T, override val n: Int) : State<T, E>() {
        override val k = n
    }

    // loading n items resulted in an error e
    data class Errored<T, E>(val e: E, override val n: Int) : State<T, E>() {
        override val k = n
    }

    companion object {
        fun <T, E> loading(k: Int, n: Int, definite: Boolean = false): State<T, E> = Loading(k, n, definite)
        fun <T, E> loaded(d: T, n: Int): State<T, E> = Loaded(d, n)
        fun <T, E> errored(e: E, n: Int): State<T, E> = Errored(e, n)
    }

}

@FlowPreview
@ExperimentalCoroutinesApi
fun <T> stateFlow(
    refresh: Flow<Unit>,
    loader: suspend () -> Either<CoopError, T>
): Flow<State<T, CoopError>> {
    val data = refresh.map { loader() }
    return listOf(
        refresh.map { State.loading<T, CoopError>(0, 1) },
        data.map {
            when (it) {
                is Either.Left -> State.errored(it.value, 1)
                is Either.Right -> State.loaded(it.value, 1)
            }
        }
    ).merge()
}

fun <T, E> Flow<State<T, E>>.loadingState(): Flow<Boolean> = map {
    when (it) {
        is State.Loading -> true
        else -> false
    }
}

fun <T, E> Flow<State<T, E>>.loadingStateIndefinite(): Flow<Boolean> = map {
    it is State.Loading && !it.definite
}

fun <T, E> Flow<State<T, E>>.loadingStateDefinite(): Flow<Pair<Int, Int>?> = map {
    when {
        it is State.Loading && it.definite -> Pair(it.k, it.n)
        else -> null
    }
}

fun <T, E> Flow<State<T, E>>.data(): Flow<T?> = map {
    when (it) {
        is State.Loaded -> it.d
        else -> null
    }
}

fun <T, E> Flow<State<T, E>>.error(): Flow<E?> = map {
    when (it) {
        is State.Errored -> it.e
        else -> null
    }
}

fun <T, U, E> Flow<State<T, E>>.mapValue(op: suspend (T) -> U): Flow<State<U, E>> = map {
    when (it) {
        is State.Loaded -> State.loaded(op(it.d), it.n)
        is State.Loading -> State.Loading(it.k, it.n, it.definite)
        is State.Errored -> State.errored(it.e, it.n)
    }
}

fun <T, U, E> Flow<State<T, E>>.flatMap(op: suspend (T, Int) -> State<U, E>): Flow<State<U, E>> = map {
    when (it) {
        is State.Loaded -> op(it.d, it.n)
        is State.Loading -> State.Loading(it.k, it.n, it.definite)
        is State.Errored -> State.errored(it.e, it.n)
    }
}

@FlowPreview
fun <T, U, E> Flow<State<T, E>>.flatMap(op: suspend (T) -> Flow<State<U, E>>): Flow<State<U, E>> = flatMapConcat {
    when (it) {
        is State.Loaded -> op(it.d)
        is State.Loading -> flowOf(State.Loading(it.k, it.n, it.definite))
        is State.Errored -> flowOf(State.errored(it.e, it.n))
    }
}

fun <A, B, Z, E> liftFlow(
    a: Flow<State<A, E>>,
    b: Flow<State<B, E>>,
    op: suspend (A, B) -> Z
): Flow<State<Z, E>> = a.combine(b) { sa, sb ->
    when {
        sa is State.Loaded && sb is State.Loaded -> State.loaded(op(sa.d, sb.d), sa.n + sb.n)
        sa is State.Errored -> State.errored(sa.e, sa.n + sb.n)
        sb is State.Errored -> State.errored(sb.e, sa.n + sb.n)
        else -> State.loading(sa.k + sb.k, sa.n + sb.n, sa.n + sb.n >= 5)
    }
}

@Suppress("unused")
fun <A, B, C, Z, E> liftFlow(
    a: Flow<State<A, E>>,
    b: Flow<State<B, E>>,
    c: Flow<State<C, E>>,
    op: suspend (A, B, C) -> Z
): Flow<State<Z, E>> =
    liftFlow(
        liftFlow(
            a,
            b
        ) { av, bv -> Pair(av, bv) },
        c
    ) { (av, bv), cv -> op(av, bv, cv) }

@Suppress("unused")
fun <A, Z, E> liftFlow(
    fs: List<Flow<State<A, E>>>,
    op: suspend (List<A>) -> Z
): Flow<State<Z, E>> {
    return fs.fold(flowOf(State.loaded<List<A>, E>(listOf(), 0))) { lifted, f ->
        liftFlow(lifted, f) { liftedv, fv -> liftedv + fv }
    }.mapValue(op)
}
