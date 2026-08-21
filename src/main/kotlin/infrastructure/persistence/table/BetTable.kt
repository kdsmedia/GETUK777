package infrastructure.persistence.table

import domain.model.BetSelection
import domain.model.BetStatus
import domain.model.BetType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.json.json
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

private val selectionsSerializer = ListSerializer(BetSelection.serializer())

object BetTable : LongIdTable("bets") {
    val externalId = varchar("external_id", 255).uniqueIndex()
    val playerId = varchar("player_id", 255).index()
    val session = reference("session_id", SportbookSessionTable)
    val currency = varchar("currency", 10)
    val betAmount = long("bet_amount")
    val winAmount = long("win_amount")
    val type = enumerationByName<BetType>("type", 20)
    val status = enumerationByName<BetStatus>("status", 20)
    val selections = json<List<BetSelection>>(
        "selections",
        { Json.encodeToString(selectionsSerializer, it) },
        { Json.decodeFromString(selectionsSerializer, it) }
    )
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}
