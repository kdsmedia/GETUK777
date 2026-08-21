package infrastructure.persistence.mapper

import domain.model.Collection
import domain.model.CasinoGame
import domain.vo.Identity
import domain.vo.ImageMap
import infrastructure.persistence.entity.CasinoGameEntity
import infrastructure.persistence.mapper.CollectionMapper.toDomain
import infrastructure.persistence.mapper.CasinoProviderMapper.toDomain
import infrastructure.persistence.mapper.CasinoProviderMapper.toCasinoProvider
import infrastructure.persistence.table.CasinoGameTable
import org.jetbrains.exposed.sql.ResultRow

object CasinoGameMapper {

    fun CasinoGameEntity.toDomain(): CasinoGame = CasinoGame(
        identity = Identity(identity),
        name = name,
        provider = provider.toDomain(),
        collections = collections.map { it.toDomain() },
        bonusBetEnable = bonusBetEnable,
        bonusWageringEnable = bonusWageringEnable,
        tags = tags,
        rtp = rtp,
        active = active,
        images = ImageMap(images.toMutableMap()),
        order = sortOrder,
    )

    fun ResultRow.toCasinoGame(collections: List<Collection> = emptyList()): CasinoGame = CasinoGame(
        identity = Identity(this[CasinoGameTable.identity]),
        name = this[CasinoGameTable.name],
        provider = toCasinoProvider(),
        collections = collections,
        bonusBetEnable = this[CasinoGameTable.bonusBetEnable],
        bonusWageringEnable = this[CasinoGameTable.bonusWageringEnable],
        tags = this[CasinoGameTable.tags],
        rtp = this[CasinoGameTable.rtp],
        active = this[CasinoGameTable.active],
        images = ImageMap(this[CasinoGameTable.images].toMutableMap()),
        order = this[CasinoGameTable.sortOrder],
    )
}
