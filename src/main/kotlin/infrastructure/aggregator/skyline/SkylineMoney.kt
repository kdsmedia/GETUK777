package infrastructure.aggregator.skyline

import domain.vo.Amount
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Skyline money is an integer number of minor units — "cents/копейки", never a decimal — while the
 * wallet works in nano (value×1e9). One minor unit is therefore 1e7 nano.
 *
 * The wallet path in [infrastructure.aggregator.skyline.webhook.SkylineWebhook] converts through
 * `ICurrencyPort` instead, so pam stays the authority on a currency's scale. This object exists for
 * the free-round grant, which is built by an adapter that the aggregator factory constructs from a
 * config map alone — no ports reach it — and where the stake is a fixed number the vendor only
 * echoes back.
 *
 * Truncation is DOWN so a stake can never grow on the way out.
 */
object SkylineMoney {

    private const val NANO_PER_MINOR_UNIT = 10_000_000L

    fun toMinorUnits(amount: Amount): Long =
        BigDecimal(amount.value).divide(BigDecimal(NANO_PER_MINOR_UNIT)).setScale(0, RoundingMode.DOWN).toLong()
}
