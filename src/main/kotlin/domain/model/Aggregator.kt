package domain.model

import domain.util.Activatable
import domain.util.AnyMapSerializer
import domain.vo.Identity
import kotlinx.serialization.Serializable

@Serializable
data class Aggregator(
    val identity: Identity,

    val integration: String,

    val type: AggregatorType = AggregatorType.CASINO,

    @Serializable(with = AnyMapSerializer::class)
    val config: Map<String, Any>,

    /**
     * Serve this aggregator's games through our own proxy domain rather than the provider's. Off by
     * default: it changes the host the player's browser talks to, which a provider must be told
     * about before it is switched on.
     */
    val isProxy: Boolean = false,

    override var active: Boolean,
) : Activatable
