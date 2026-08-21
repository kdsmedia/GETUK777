package application.command.game

import application.ICommand
import kotlinx.datetime.Instant

/**
 * Recomputes [domain.model.CasinoGame.rtp] from the spins whose round started at/after
 * [since]. Only games with bets in the window are updated. Returns the number of
 * updated games. Dispatched by the daily-rtp job.
 */
data class RecalculateCasinoGameRtpCommand(val since: Instant) : ICommand<Int>
