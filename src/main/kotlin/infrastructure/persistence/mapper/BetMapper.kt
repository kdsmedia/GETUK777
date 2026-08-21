package infrastructure.persistence.mapper

import domain.model.Bet
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.ExternalBetId
import domain.vo.PlayerId
import infrastructure.persistence.entity.BetEntity
import infrastructure.persistence.mapper.SportbookSessionMapper.toDomain

object BetMapper {

    fun BetEntity.toDomain(): Bet = Bet(
        id = id.value,
        externalId = ExternalBetId(externalId),
        playerId = PlayerId(playerId),
        session = session.toDomain(),
        currency = Currency(currency),
        betAmount = Amount(betAmount),
        winAmount = Amount(winAmount),
        type = type,
        status = status,
        selections = selections,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
