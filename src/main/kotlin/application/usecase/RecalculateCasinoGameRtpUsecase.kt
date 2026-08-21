package application.usecase

import domain.model.CasinoGame
import domain.model.Spin
import domain.repository.ICasinoGameRepository
import domain.repository.ISpinRepository
import kotlin.math.roundToLong
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory

/**
 * Recomputes [CasinoGame.rtp] = (settled winnings / placed bets) * 100 from the spins
 * whose round started at/after `since`. Only games that had bets in the window
 * are saved — the rest keep their previous RTP untouched.
 */
class RecalculateCasinoGameRtpUsecase(
    private val spinRepository: ISpinRepository,
    private val gameRepository: ICasinoGameRepository,
) {

    private val logger = LoggerFactory.getLogger(RecalculateCasinoGameRtpUsecase::class.java)

    suspend operator fun invoke(since: Instant): Result<Int> = runCatching {
        val spins = spinRepository.findAllSince(since)
        logger.info("RTP recalculation: {} spins since {}", spins.size, since)

        val updatedGames = spins
            .groupBy { it.round.gameVariant.game.identity }
            .values
            .mapNotNull(::recalculate)

        gameRepository.saveAll(updatedGames)
        logger.info("RTP recalculation: {} game(s) updated", updatedGames.size)

        updatedGames.size
    }

    private fun recalculate(gameSpins: List<Spin>): CasinoGame? {
        val bets = gameSpins.filter { it.isPlace }.sumOf { it.amount.value } -
            gameSpins.filter { it.isRollback }.sumOf { it.amount.value }
        if (bets <= 0L) return null

        val wins = gameSpins.filter { it.isSettle }.sumOf { it.amount.value }
        val rtp = (wins.toDouble() / bets.toDouble() * 100.0 * 100.0).roundToLong() / 100.0

        return gameSpins.first().round.gameVariant.game.copy(rtp = rtp)
    }
}
