package infrastructure.aggregator.tech01sport.webhook.dto

import kotlinx.serialization.Serializable

/** The aggregator's bet snapshot as sent in `credit-bet` and `debit-bet-by-batch`. */
@Serializable
data class BetPayloadDto(
    val id: Long,

    val betType: String = "",

    val status: Int = 0,

    val selections: List<SelectionDto> = emptyList(),
)

@Serializable
data class SelectionDto(
    val matchId: Long? = null,

    val homeTeamName: String? = null,

    val awayTeamName: String? = null,

    val market: String? = null,

    val sportName: String? = null,

    val tournamentName: String? = null,

    val dateOfMatch: Long? = null,
)
