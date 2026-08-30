package infrastructure.aggregator.onegamehub.adapter

import application.port.external.IFreespinPort
import domain.vo.Currency
import domain.vo.PlayerId
import infrastructure.aggregator.onegamehub.OneGameHubConfig
import infrastructure.aggregator.onegamehub.client.OneGameHubHttpClient
import infrastructure.aggregator.onegamehub.client.dto.CancelFreespinDto
import infrastructure.aggregator.onegamehub.client.dto.CreateFreespinDto
import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal
import java.math.RoundingMode

class OneGameHubFreespinAdapter(
    config: OneGameHubConfig,
) : IFreespinPort {

    private val client = OneGameHubHttpClient(config)

    private companion object {
        const val NANO_PER_MINOR_UNIT = 10_000_000L
    }

    override suspend fun getPreset(gameSymbol: String): Map<String, Any> {
        val response = client.listGames()

        check(response.success) { "OneGameHub listGames failed with ${response.describe()}" }

        val game = response.response
            ?.firstOrNull { it.id == gameSymbol }
            ?: error("Game $gameSymbol not found in OneGameHub")

        return mapOf(
            "paylines" to game.paylines,
            "freespinEnable" to game.freespinEnable
        )
    }

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
        val bet = spinAmount.toProviderBet()
        val number = spinCount
        val lineNumber = (presetValue["paylines"] as? Number)?.toInt() ?: 0

        val payload = CreateFreespinDto(
            id = referenceId,
            startAt = startAt,
            endAt = endAt,
            number = number,
            playerId = playerId.value,
            currency = currency.value,
            gameId = gameSymbol,
            bet = bet,
            lineNumber = lineNumber
        )

        val response = client.createFreespin(payload)

        check(response.success) { "OneGameHub createFreespin failed with ${response.describe()}" }
    }

    /**
     * The stake reaches us in the wallet's system unit (nano); OneGameHub counts free-round bets in
     * MINOR units, so a UAH 2 spin goes out as 200. Sending nano straight through is what `toInt()`
     * used to do — `bet` was 2 000 000 000 for that same spin, and the provider takes it without a
     * word, so the value only shows up inside the game.
     *
     * The unit is the provider's, not ours: it rejects a bet below its own minimum with a flat
     * "Cant issue freerounds" and validates nothing else, so a wrong scale fails as a refusal to
     * issue rather than as a message that says what is wrong.
     */
    private fun Long.toProviderBet(): Int =
        BigDecimal(this).divide(BigDecimal(NANO_PER_MINOR_UNIT)).setScale(0, RoundingMode.DOWN).toInt()

    override suspend fun cancel(referenceId: String) {
        val payload = CancelFreespinDto(id = referenceId)

        val response = client.cancelFreespin(payload)

        check(response.success) { "OneGameHub cancelFreespin failed with ${response.describe()}" }
    }
}
