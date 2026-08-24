package infrastructure.aggregator.skyline.adapter

import application.port.external.IFreespinPort
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.PlayerId
import infrastructure.aggregator.skyline.SkylineAction
import infrastructure.aggregator.skyline.SkylineConfig
import infrastructure.aggregator.skyline.SkylineMoney
import infrastructure.aggregator.skyline.client.SkylineHttpClient
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Free Rounds Bonus. A grant is a constant stake times a number of rounds, scoped to one game, and
 * it is identified end to end by OUR reference id — the same value comes back as the `bonus` field
 * of every callback the free rounds produce.
 *
 * The stake is only echoed, never charged: the vendor sends `bet_amount: 0` for a free round and
 * the engine's own free-round path does not touch the balance either.
 */
class SkylineFreespinAdapter(
    private val config: SkylineConfig,
) : IFreespinPort {

    private val client = SkylineHttpClient(config)

    /** Their grant needs nothing from the game beyond its id — no paylines, no bet table — so
     *  there is no preset to fetch and the operator's own stake and count are the whole input. */
    override suspend fun getPreset(gameSymbol: String): Map<String, Any> = emptyMap()

    override suspend fun create(
        presetValue: Map<String, Any>,
        referenceId: String,
        playerId: PlayerId,
        gameSymbol: String,
        currency: Currency,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
        spinAmount: Long,
        spinCount: Int
    ) {
        check(config.casino.isNotBlank()) { "Skyline casino key not configured" }

        val params = mapOf<String, JsonElement>(
            FIELD_CASINO to JsonPrimitive(config.casino),
            FIELD_BONUS to JsonPrimitive(referenceId),
            FIELD_BET to JsonPrimitive(SkylineMoney.toMinorUnits(Amount(spinAmount))),
            FIELD_QUANTITY to JsonPrimitive(spinCount),
            FIELD_CURRENCY to JsonPrimitive(currency.value),
            FIELD_PLAYER_ID to JsonPrimitive(playerId.value),
            // Scoped to the one game the grant was issued on: `only` plus an explicit id is how
            // their filter narrows, and the alternative (`*`) would open every game we carry.
            FIELD_FILTER to JsonPrimitive(FILTER_ONLY),
            FIELD_GAMES to JsonPrimitive(gameSymbol),
            FIELD_DESCRIPTION to JsonPrimitive(config.bonusDescription),
            FIELD_START to JsonPrimitive(startAt.format()),
            FIELD_EXPIRATION to JsonPrimitive(endAt.format()),
        )

        client.call(SkylineAction.BONUS_AWARD, params)
    }

    override suspend fun cancel(referenceId: String) {
        check(config.casino.isNotBlank()) { "Skyline casino key not configured" }

        client.call(
            SkylineAction.BONUS_CANCEL,
            mapOf(
                FIELD_CASINO to JsonPrimitive(config.casino),
                FIELD_BONUS to JsonPrimitive(referenceId),
            ),
        )
    }

    /** `YYYY-MM-DD HH:MM:SS` — their format, and not one `LocalDateTime.toString()` produces
     *  (it emits an ISO `T` and drops zero seconds). */
    private fun LocalDateTime.format(): String = "%04d-%02d-%02d %02d:%02d:%02d".format(
        year, monthNumber, dayOfMonth, hour, minute, second,
    )

    private companion object {
        const val FIELD_CASINO = "casino"

        const val FIELD_BONUS = "bonus"

        const val FIELD_BET = "bet"

        const val FIELD_QUANTITY = "quantity"

        const val FIELD_CURRENCY = "currency"

        const val FIELD_PLAYER_ID = "player_id"

        const val FIELD_FILTER = "filter"

        const val FIELD_GAMES = "games"

        const val FIELD_DESCRIPTION = "description"

        const val FIELD_START = "start"

        const val FIELD_EXPIRATION = "expiration"

        const val FILTER_ONLY = "only"
    }
}
