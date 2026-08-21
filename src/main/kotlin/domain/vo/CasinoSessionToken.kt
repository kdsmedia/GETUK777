package domain.vo

import domain.exception.badrequest.BlankCasinoSessionTokenException
import domain.exception.domainRequire
import kotlinx.serialization.Serializable

/**
 * CasinoSession token value object.
 */
@Serializable
@JvmInline
value class CasinoSessionToken(val value: String) {
    init {
        domainRequire(value.isNotBlank()) { BlankCasinoSessionTokenException() }
    }
}
