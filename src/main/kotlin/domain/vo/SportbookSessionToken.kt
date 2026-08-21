package domain.vo

import domain.exception.badrequest.BlankSportbookSessionTokenException
import domain.exception.domainRequire
import kotlinx.serialization.Serializable

/**
 * SportbookSession token value object — the public token handed to the provider frontend.
 */
@Serializable
@JvmInline
value class SportbookSessionToken(val value: String) {
    init {
        domainRequire(value.isNotBlank()) { BlankSportbookSessionTokenException() }
    }
}
