package infrastructure.persistence.table

import domain.model.Platform
import org.jetbrains.exposed.dao.id.LongIdTable

object CasinoSessionTable : LongIdTable("casino_sessions") {
    val gameVariant = reference("game_variant_id", CasinoGameVariantTable)
    val playerId = varchar("player_id", 255).index()
    val token = varchar("token", 1024).uniqueIndex()
    val externalToken = varchar("external_token", 1024).nullable()
    val currency = varchar("currency", 10)
    val locale = varchar("locale", 10)
    val platform = enumerationByName<Platform>("platform", 20)
}
