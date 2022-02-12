package de.lorenzgorse.coopmobile.ui.correspondences

import android.app.Application
import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.Correspondence
import de.lorenzgorse.coopmobile.client.CorrespondenceHeader
import de.lorenzgorse.coopmobile.data.CoopViewModel
import de.lorenzgorse.coopmobile.data.State
import de.lorenzgorse.coopmobile.data.flatMap
import de.lorenzgorse.coopmobile.data.liftFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore

@ExperimentalCoroutinesApi
@FlowPreview
class CorrespondencesData(app: Application) : CoopViewModel(app) {

    val state: Flow<State<List<Correspondence>, CoopError>> =
        load { client.getCorrespondeces() }.flatMap(::loadFromHeaders).share()

    private fun loadFromHeaders(headers: List<CorrespondenceHeader>): Flow<State<List<Correspondence>, CoopError>> {
        // Loading all correspondences at once sometimes leads to timeouts, so we limit the
        // concurrency to 5.
        val semaphore = Semaphore(5)

        val augmented = headers.map { header ->
            loadNow {
                semaphore.acquire()
                try {
                    client.augmentCorrespondence(header)
                } finally {
                    semaphore.release()
                }
            }
        }

        return liftFlow(augmented) { it }
    }

}
