package infrastructure.aggregator.gamingflow.adapter

import application.port.external.IFreespinPort
import domain.exception.conflict.FreespinNotSupportedException
import domain.exception.domainRequire
import domain.vo.Currency
import domain.vo.PlayerId
import infrastructure.aggregator.gamingflow.GamingFlowConfig
import infrastructure.aggregator.gamingflow.client.GamingFlowHttpClient
import kotlinx.datetime.LocalDateTime

/**
 * GamingFlow splits free rounds across both sides: the provider only knows a bonus *id*, while the
 * remaining-round counter lives with us and is served through the Seamless API (`freeroundsLeft` on
 * `getBalance`, decremented via `chargeFreerounds` on `withdrawAndDeposit`). So creation here is
 * nothing more than registering the id the provider will accept as `BonusId` on a session; the
 * count, stake and validity window are enforced locally.
 */
class GamingFlowFreespinAdapter(
    config: GamingFlowConfig,
) : IFreespinPort {

    private val client = GamingFlowHttpClient(config)

    override suspend fun getPreset(gameSymbol: String): Map<String, Any> {
        val game = client.listGames().firstOrNull { it.id == gameSymbol }
            ?: error("Game $gameSymbol not found in GamingFlow")

        return mapOf(
            FREEROUND_SUPPORTED to (FREEROUND_TAG in game.tags),
            BASE_BET to game.baseBet,
            LINES_COUNT to game.linesCount
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
        // A session created with a BonusId is rejected unless the game carries the FR tag.
        domainRequire(presetValue[FREEROUND_SUPPORTED] == true) { FreespinNotSupportedException() }

        client.setBonus(referenceId)
    }

    /**
     * No-op: the provider exposes no bonus deletion, and it holds no free-round state to delete —
     * a bonus id it never charges is inert. Revoking the rounds means zeroing our own counter, which
     * the caller has already done by the time it gets here.
     */
    override suspend fun cancel(referenceId: String) = Unit

    private companion object {
        const val FREEROUND_SUPPORTED = "freeroundSupported"

        const val BASE_BET = "baseBet"

        const val LINES_COUNT = "linesCount"

        const val FREEROUND_TAG = "FR"
    }
}
