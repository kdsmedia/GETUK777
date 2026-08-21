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

    override var active: Boolean,
) : Activatable
