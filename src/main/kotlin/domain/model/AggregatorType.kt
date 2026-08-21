package domain.model

import kotlinx.serialization.Serializable

/** Product type of an aggregator integration. */
@Serializable
enum class AggregatorType {
    CASINO,
    SPORTBOOK
}
