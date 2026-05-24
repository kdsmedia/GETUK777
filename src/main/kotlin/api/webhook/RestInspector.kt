package api.webhook

import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.doublereceive.DoubleReceive
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.ApplicationResponse
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("RestInspector")

/**
 * Global REST inspector — logs every inbound HTTP request and its response, **with bodies**.
 * The built-in `CallLogging` only logs the request line + status + duration; this adds the
 * JSON payloads, which is what you need to debug webhook traffic (e.g. balance mismatches).
 *
 * Request bodies are read via `DoubleReceive` so the route handler can still receive them.
 */
val RestInspector = createApplicationPlugin(name = "RestInspector") {

    onCall { call ->
        val body = runCatching { call.receiveText() }.getOrDefault("")
        logger.info(
            "→ {} {}{}",
            call.request.httpMethod.value,
            call.request.uri,
            if (body.isBlank()) "" else " body=$body",
        )
    }

    onCallRespond { call, body ->
        logger.info(
            "← {} {} {} body={}",
            call.request.httpMethod.value,
            call.request.uri,
            call.response.statusOrDash(),
            body,
        )
    }
}

private fun ApplicationResponse.statusOrDash(): String = status()?.value?.toString() ?: "-"

fun Application.configureRestInspector() {
    install(DoubleReceive)
    install(RestInspector)
}
