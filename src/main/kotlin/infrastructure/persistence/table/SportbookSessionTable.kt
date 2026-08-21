package infrastructure.persistence.table

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.json.json
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

private val dataSerializer = MapSerializer(String.serializer(), String.serializer())

object SportbookSessionTable : LongIdTable("sportbook_sessions") {
    val token = varchar("token", 255).uniqueIndex()
    val externalToken = varchar("external_token", 255).nullable()
    val playerId = varchar("player_id", 255).index()
    val currency = varchar("currency", 10)
    val aggregator = reference("aggregator_id", AggregatorTable)
    val data = json<Map<String, String>>(
        "data",
        { Json.encodeToString(dataSerializer, it) },
        { Json.decodeFromString(dataSerializer, it) }
    )
    val createdAt = timestamp("created_at")
}
