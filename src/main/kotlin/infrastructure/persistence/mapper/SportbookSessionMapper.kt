package infrastructure.persistence.mapper

import domain.model.SportbookSession
import domain.vo.Currency
import domain.vo.PlayerId
import domain.vo.SportbookSessionToken
import infrastructure.persistence.entity.SportbookSessionEntity
import infrastructure.persistence.mapper.AggregatorMapper.toDomain

object SportbookSessionMapper {

    fun SportbookSessionEntity.toDomain(): SportbookSession = SportbookSession(
        id = id.value,
        token = SportbookSessionToken(token),
        externalToken = externalToken,
        playerId = PlayerId(playerId),
        currency = Currency(currency),
        aggregator = aggregator.toDomain(),
        data = data,
        createdAt = createdAt,
    )
}
