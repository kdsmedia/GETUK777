package domain.vo

import domain.exception.badrequest.BlankCasinoGameSymbolException
import domain.exception.domainRequire
import kotlinx.serialization.Serializable

/**
 * Aggregator-side game symbol. Unique within an integration; used by aggregators to
 * identify which game variant the player is launching/spinning.
 */
@Serializable
@JvmInline
value class CasinoGameSymbol(val value: String) {
    init {
        domainRequire(value.isNotBlank()) { BlankCasinoGameSymbolException() }
    }
}
