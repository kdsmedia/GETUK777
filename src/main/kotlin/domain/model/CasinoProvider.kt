package domain.model

import domain.exception.badrequest.UnsupportedAggregatorTypeException
import domain.exception.domainRequire
import domain.util.Activatable
import domain.util.Imageable
import domain.util.Orderable
import domain.vo.Country
import domain.vo.Identity
import domain.vo.ImageMap
import kotlinx.serialization.Serializable

@Serializable
data class CasinoProvider(
    val identity: Identity,

    val name: String,

    override var images: ImageMap = ImageMap.EMPTY,

    override var order: Int = 100,

    override var active: Boolean = false,

    val aggregator: Aggregator,

    val blockedCountry: List<Country> = emptyList(),

    val tags: List<String> = emptyList(),

    /** Other spellings of this same vendor, as other aggregators name it (`egt` for `amusnet`,
     *  `pragmatic` for `pragmatic_play`). The sync collapses a feed's provider onto this row when
     *  it matches one of these, so a vendor never lands in the catalog twice. */
    val aliases: List<String> = emptyList(),
) : Activatable, Imageable, Orderable {
    init {
        domainRequire(aggregator.type == AggregatorType.CASINO) { UnsupportedAggregatorTypeException(aggregator.type) }
    }
}
