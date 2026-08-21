package infrastructure.persistence.entity

import infrastructure.persistence.table.CasinoGameFavouriteTable
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class CasinoGameFavouriteEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<CasinoGameFavouriteEntity>(CasinoGameFavouriteTable)

    var gameId by CasinoGameFavouriteTable.game
    var playerId by CasinoGameFavouriteTable.playerId
}
