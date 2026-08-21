package infrastructure.aggregator.tech01sport.webhook

import domain.model.BetSelection
import domain.model.BetType
import infrastructure.aggregator.tech01sport.webhook.dto.SelectionDto
import kotlinx.datetime.Instant

/** Maps the aggregator's bet payload vocabulary onto the domain. */
object Tech01SportBetMapper {

    /** `bet.status` value meaning the bet is won; everything else settles as lost. */
    const val STATUS_WIN = 2

    fun toBetType(value: String): BetType = when (value.lowercase()) {
        "express" -> BetType.COMBO
        "system" -> BetType.SYSTEM
        else -> BetType.SINGLE
    }

    fun SelectionDto.toDomain(): BetSelection = BetSelection(
        matchId = matchId?.toString() ?: "",
        homeTeamName = homeTeamName ?: "",
        awayTeamName = awayTeamName ?: "",
        market = market ?: "",
        sportName = sportName ?: "",
        tournamentName = tournamentName ?: "",
        matchDate = dateOfMatch?.let { Instant.fromEpochSeconds(it) },
    )
}
