package infrastructure.persistence.table

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object FreespinTable : LongIdTable("freespins") {
    // Unique: the reference is the id shared with the provider, and it is what an inbound call
    // resolves the grant by.
    val referenceId = varchar("reference_id", 255).uniqueIndex()
    val playerId = varchar("player_id", 255)
    val gameVariant = reference("game_variant_id", CasinoGameVariantTable)
    val currency = varchar("currency", 8)
    val spinAmount = long("spin_amount")
    val totalCount = integer("total_count")
    val remainingCount = integer("remaining_count")
    val startAt = timestamp("start_at")
    val endAt = timestamp("end_at")
    val cancelledAt = timestamp("cancelled_at").nullable()
    val createdAt = timestamp("created_at")

    init {
        index(false, playerId, gameVariant)
    }
}
