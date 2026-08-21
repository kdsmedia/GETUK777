package infrastructure.aggregator.tech01sport

import domain.vo.Amount
import java.math.BigDecimal

/**
 * 01.tech money on the wire is a signed decimal string ("153.5", "-10"); the wallet works in
 * nano units (value×1e9). The sign travels separately — [Amount] is unsigned by design, the
 * caller decides deposit vs withdraw via [isNegative].
 */
object Tech01SportMoney {

    private const val WALLET_SCALE = 9

    fun toAmount(value: String): Amount =
        Amount(BigDecimal(value).abs().movePointRight(WALLET_SCALE).longValueExact())

    fun fromAmount(amount: Amount): String =
        BigDecimal(amount.value).movePointLeft(WALLET_SCALE).stripTrailingZeros().toPlainString()

    fun isNegative(value: String): Boolean = BigDecimal(value).signum() < 0
}
