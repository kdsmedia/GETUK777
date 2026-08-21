package infrastructure.persistence.entity

import infrastructure.persistence.table.BetTable
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class BetEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<BetEntity>(BetTable)

    var externalId by BetTable.externalId
    var playerId by BetTable.playerId
    var session by SportbookSessionEntity referencedOn BetTable.session
    var currency by BetTable.currency
    var betAmount by BetTable.betAmount
    var winAmount by BetTable.winAmount
    var type by BetTable.type
    var status by BetTable.status
    var selections by BetTable.selections
    var createdAt by BetTable.createdAt
    var updatedAt by BetTable.updatedAt
}
