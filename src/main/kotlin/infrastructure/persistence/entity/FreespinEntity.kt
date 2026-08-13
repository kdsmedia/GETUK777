package infrastructure.persistence.entity

import infrastructure.persistence.table.FreespinTable
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class FreespinEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<FreespinEntity>(FreespinTable)

    var referenceId by FreespinTable.referenceId
    var playerId by FreespinTable.playerId
    var gameVariant by GameVariantEntity referencedOn FreespinTable.gameVariant
    var currency by FreespinTable.currency
    var spinAmount by FreespinTable.spinAmount
    var totalCount by FreespinTable.totalCount
    var remainingCount by FreespinTable.remainingCount
    var startAt by FreespinTable.startAt
    var endAt by FreespinTable.endAt
    var cancelledAt by FreespinTable.cancelledAt
    var createdAt by FreespinTable.createdAt
}
