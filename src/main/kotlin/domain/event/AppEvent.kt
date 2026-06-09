package domain.event

import kotlinx.serialization.KSerializer

/**
 * Uniform event contract. Every event carries exactly one [data] payload; the correlation key
 * [playerId] is derived from [data]. The companion [Meta] supplies the route, the payload
 * serializer, and a rebuild factory — the only place that knows how to wire the event.
 */
interface AppEvent<T : Any> {
    val playerId: String

    val data: T

    interface Meta<T : Any> {
        val route: String

        val serializer: KSerializer<T>

        fun create(data: T): AppEvent<T>
    }
}
