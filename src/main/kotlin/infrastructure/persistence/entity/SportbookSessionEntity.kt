package infrastructure.persistence.entity

import infrastructure.persistence.table.SportbookSessionTable
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class SportbookSessionEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<SportbookSessionEntity>(SportbookSessionTable)

    var token by SportbookSessionTable.token
    var externalToken by SportbookSessionTable.externalToken
    var playerId by SportbookSessionTable.playerId
    var currency by SportbookSessionTable.currency
    var aggregator by AggregatorEntity referencedOn SportbookSessionTable.aggregator
    var data by SportbookSessionTable.data
    var createdAt by SportbookSessionTable.createdAt
}
