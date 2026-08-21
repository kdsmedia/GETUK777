package domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class BetSelection(
    val matchId: String,

    val homeTeamName: String,

    val awayTeamName: String,

    val market: String,

    val sportName: String,

    val tournamentName: String,

    val matchDate: Instant? = null,
)
