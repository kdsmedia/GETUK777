package infrastructure.persistence.table

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object CasinoRoundTable : LongIdTable("casino_rounds") {
    val externalId = varchar("external_id", 255)
    val freespinId = varchar("freespin_id", 255).nullable()
    val session = reference("session_id", CasinoSessionTable)
    val gameVariant = reference("game_variant_id", CasinoGameVariantTable)
    val createdAt = timestamp("created_at")
    val finishedAt = timestamp("finished_at").nullable()

    init {
        index(isUnique = true, externalId, session)
    }
}
