package domain.vo

import domain.exception.badrequest.BlankExternalIdException
import domain.exception.domainRequire
import kotlinx.serialization.Serializable

/** External round identifier issued by the aggregator. */
@Serializable
@JvmInline
value class ExternalCasinoRoundId(val value: String) {
    init {
        domainRequire(value.isNotBlank()) { BlankExternalIdException() }
    }
}
