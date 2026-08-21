package domain.vo

import domain.exception.badrequest.BlankExternalIdException
import domain.exception.domainRequire
import kotlinx.serialization.Serializable

/** External bet identifier issued by the sportsbook aggregator. */
@Serializable
@JvmInline
value class ExternalBetId(val value: String) {
    init {
        domainRequire(value.isNotBlank()) { BlankExternalIdException() }
    }
}
