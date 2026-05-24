package infrastructure.util

import application.port.external.IBackgroundTaskPort
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

class BackgroundWorker : IBackgroundTaskPort, CoroutineScope {
    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.IO + CoroutineName("background")

    private val logger = LoggerFactory.getLogger(BackgroundWorker::class.java)

    override suspend fun launch(action: suspend () -> Unit, error: suspend () -> Unit) {
        launch {
            try {
                action()
            } catch (t: Throwable) {
                logger.error("Background task failed", t)
                error()
            }
        }
    }
}