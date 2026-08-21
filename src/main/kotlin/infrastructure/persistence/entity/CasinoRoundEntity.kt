package infrastructure.persistence.entity

import infrastructure.persistence.table.CasinoRoundTable
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class CasinoRoundEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<CasinoRoundEntity>(CasinoRoundTable)

    var externalId by CasinoRoundTable.externalId
    var freespinId by CasinoRoundTable.freespinId
    var session by CasinoSessionEntity referencedOn CasinoRoundTable.session
    var gameVariant by CasinoGameVariantEntity referencedOn CasinoRoundTable.gameVariant
    var createdAt by CasinoRoundTable.createdAt
    var finishedAt by CasinoRoundTable.finishedAt
}
