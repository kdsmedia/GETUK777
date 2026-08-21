package infrastructure.persistence.entity

import infrastructure.persistence.table.CasinoSessionTable
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class CasinoSessionEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<CasinoSessionEntity>(CasinoSessionTable)

    var gameVariant by CasinoGameVariantEntity referencedOn CasinoSessionTable.gameVariant
    var playerId by CasinoSessionTable.playerId
    var token by CasinoSessionTable.token
    var externalToken by CasinoSessionTable.externalToken
    var currency by CasinoSessionTable.currency
    var locale by CasinoSessionTable.locale
    var platform by CasinoSessionTable.platform
}
