package infrastructure.persistence.mapper

import domain.model.CasinoRound
import domain.vo.ExternalCasinoRoundId
import domain.vo.FreespinId
import infrastructure.persistence.entity.CasinoRoundEntity
import infrastructure.persistence.mapper.CasinoGameVariantMapper.toDomain
import infrastructure.persistence.mapper.CasinoSessionMapper.toDomain

object CasinoRoundMapper {

    fun CasinoRoundEntity.toDomain(): CasinoRound = CasinoRound(
        id = id.value,
        externalId = ExternalCasinoRoundId(externalId),
        freespinId = freespinId?.let { FreespinId(it) },
        session = session.toDomain(),
        gameVariant = gameVariant.toDomain(),
        createdAt = createdAt,
        finishedAt = finishedAt,
    )
}
