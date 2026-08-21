package infrastructure.persistence.table

import domain.model.AggregatorType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.json.json

object AggregatorTable : LongIdTable("aggregators") {
    val identity = varchar("identity", 255).uniqueIndex()
    val integration = varchar("integration", 255)
    val type = enumerationByName<AggregatorType>("type", 20).default(AggregatorType.CASINO)
    val config = json(
        "config",
        { Json.encodeToString(JsonObject.serializer(), it) },
        { Json.decodeFromString(JsonObject.serializer(), it) }
    )
    val active = bool("active")
}
