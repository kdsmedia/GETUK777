package infrastructure.persistence.table

import domain.model.SpinType
import org.jetbrains.exposed.dao.id.LongIdTable

object SpinTable : LongIdTable("spins") {
    // Unique, not merely indexed: it is the idempotency key for a provider transaction, and the
    // constraint is what makes a concurrent redelivery collide instead of moving money twice.
    val externalId = varchar("external_id", 255).uniqueIndex()
    val round = reference("round_id", CasinoRoundTable).index()
    val reference = reference("reference_id", SpinTable).nullable()
    val type = enumerationByName<SpinType>("type", 20)
    val amount = long("amount")
    val realAmount = long("real_amount")
    val bonusAmount = long("bonus_amount")
}
