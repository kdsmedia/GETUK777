package infrastructure.persistence.repository

import domain.exception.conflict.SpinAlreadyExistsException
import domain.model.Spin
import domain.repository.ISpinRepository
import infrastructure.persistence.dbRead
import infrastructure.persistence.dbTransaction
import infrastructure.persistence.entity.GameEntity
import infrastructure.persistence.entity.GameVariantEntity
import infrastructure.persistence.entity.ProviderEntity
import infrastructure.persistence.entity.RoundEntity
import infrastructure.persistence.entity.SessionEntity
import infrastructure.persistence.entity.SpinEntity
import infrastructure.persistence.mapper.SpinMapper.toDomain
import infrastructure.persistence.table.RoundTable
import infrastructure.persistence.table.SpinTable
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.update
import java.sql.SQLException

class SpinRepositoryImpl : ISpinRepository {

    private companion object {
        const val UNIQUE_VIOLATION_SQL_STATE = "23505"
    }

    override suspend fun save(spin: Spin): Spin = dbTransaction {
        if (spin.id == Long.MIN_VALUE) {
            val id = try {
                SpinTable.insertAndGetId { it.fromDomain(spin) }
            } catch (e: ExposedSQLException) {
                if (e.isUniqueViolation()) throw SpinAlreadyExistsException() else throw e
            }
            spin.copy(id = id.value)
        } else {
            SpinTable.update({ SpinTable.id eq spin.id }) { it.fromDomain(spin) }
            spin
        }
    }

    override suspend fun findById(id: Long): Spin? = dbRead {
        SpinEntity.findById(id)?.toDomain()
    }

    override suspend fun findByExternalId(externalId: String): Spin? = dbRead {
        SpinEntity.find { SpinTable.externalId eq externalId }
            .firstOrNull()?.toDomain()
    }

    override suspend fun findAllSince(since: Instant): List<Spin> = dbRead {
        val rows = SpinTable.innerJoin(RoundTable)
            .select(SpinTable.columns)
            .where { RoundTable.createdAt greaterEq since }

        SpinEntity.wrapRows(rows)
            .with(
                SpinEntity::reference,
                SpinEntity::round,
                RoundEntity::session,
                RoundEntity::gameVariant,
                SessionEntity::gameVariant,
                GameVariantEntity::game,
                GameEntity::provider,
                GameEntity::collections,
                ProviderEntity::aggregator,
            )
            .map { it.toDomain() }
    }

    /** Postgres `unique_violation`; the driver exposes it as the SQLState, not as a typed error. */
    private fun ExposedSQLException.isUniqueViolation(): Boolean =
        generateSequence(cause) { it.cause }
            .filterIsInstance<SQLException>()
            .any { it.sqlState == UNIQUE_VIOLATION_SQL_STATE }

    private fun UpdateBuilder<*>.fromDomain(spin: Spin) {
        this[SpinTable.externalId] = spin.externalId.value
        this[SpinTable.round] = spin.round.id
        this[SpinTable.reference] = spin.reference?.id
        this[SpinTable.type] = spin.type
        this[SpinTable.amount] = spin.amount.value
        this[SpinTable.realAmount] = spin.realAmount.value
        this[SpinTable.bonusAmount] = spin.bonusAmount.value
    }
}
