package infrastructure.persistence.mapper

import domain.model.Freespin
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.FreespinId
import domain.vo.PlayerId
import infrastructure.persistence.entity.FreespinEntity
import infrastructure.persistence.mapper.CasinoGameVariantMapper.toDomain

object FreespinMapper {

    fun FreespinEntity.toDomain(): Freespin = Freespin(
        id = id.value,
        referenceId = FreespinId(referenceId),
        playerId = PlayerId(playerId),
        gameVariant = gameVariant.toDomain(),
        currency = Currency(currency),
        spinAmount = Amount(spinAmount),
        totalCount = totalCount,
        remainingCount = remainingCount,
        startAt = startAt,
        endAt = endAt,
        cancelledAt = cancelledAt,
        createdAt = createdAt,
    )
}
