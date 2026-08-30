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
        const val NANO_PER_UNIT = 1_000_000_000L
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
     * The stake reaches us in the wallet's system unit (nano) and OneGameHub counts money in whole
     * currency units — the same units its webhook posts back as a decimal `amount`. Sending nano
     * straight through is what `toInt()` used to do: `bet` went out as 2 000 000 000 for a UAH 2 spin,
     * and the provider accepts it without a word, so nothing downstream catches it.
     *
     * `bet` is an integer on their wire, so a sub-unit stake cannot be expressed at all and rounds
     * down to zero; that is the provider's contract, not a rounding choice made here.
     */
    private fun Long.toProviderBet(): Int =
        BigDecimal(this).divide(BigDecimal(NANO_PER_UNIT)).setScale(0, RoundingMode.DOWN).toInt()

    override suspend fun cancel(referenceId: String) {
        val payload = CancelFreespinDto(id = referenceId)

        val response = client.cancelFreespin(payload)

        check(response.success) { "OneGameHub cancelFreespin failed with ${response.describe()}" }
    }
}
