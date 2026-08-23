package infrastructure.aggregator.gambutsoft

import domain.vo.Amount
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

/**
 * Gambutsoft money on the wire is a MAJOR-unit decimal (`bet: 25` is 25.00, `balance: 2500` is
 * 2500.00), and some of their sub-vendors send it as a JSON string instead of a number. The wallet
 * works in nano units (value×1e9).
 *
 * Truncation is deliberate: an amount carrying more precision than the wallet can hold is cut
 * DOWN, never rounded up, so a stake can never grow on the way in. Without the explicit scale
 * `longValueExact` would throw a raw `ArithmeticException` instead. A negative amount is rejected
 * by [Amount] itself, which is the only sign the vendor is allowed to send.
 */
object GambutsoftMoney {

    private const val WALLET_SCALE = 9

    fun toAmount(value: String): Amount =
        Amount(BigDecimal(value).movePointRight(WALLET_SCALE).setScale(0, RoundingMode.DOWN).longValueExact())

    /** Emitted unquoted: their `balance` field is typed as a number, not a string. */
    fun toJsonNumber(amount: Amount): JsonPrimitive = JsonUnquotedLiteral(
        BigDecimal(amount.value).movePointLeft(WALLET_SCALE).stripTrailingZeros().toPlainString()
    )
}
