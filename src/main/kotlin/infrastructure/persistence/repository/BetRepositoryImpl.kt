package infrastructure.persistence.repository

import domain.model.Bet
import domain.repository.IBetRepository
import domain.vo.ExternalBetId
import infrastructure.persistence.dbRead
import infrastructure.persistence.dbTransaction
import infrastructure.persistence.entity.BetEntity
import infrastructure.persistence.entity.SportbookSessionEntity
import infrastructure.persistence.mapper.BetMapper.toDomain
import infrastructure.persistence.table.BetTable
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.update

class BetRepositoryImpl : IBetRepository {

    private val betChain = arrayOf(
        BetEntity::session,
        SportbookSessionEntity::aggregator,
    )

    override suspend fun save(bet: Bet): Bet = dbTransaction {
        if (bet.id == Long.MIN_VALUE) {
            val id = BetTable.insertAndGetId { it.fromDomain(bet) }
            bet.copy(id = id.value)
        } else {
            BetTable.update({ BetTable.id eq bet.id }) { it.fromDomain(bet) }
            bet
        }
    }

    override suspend fun findById(id: Long): Bet? = dbRead {
        BetEntity.findById(id)
            ?.load(*betChain)
            ?.toDomain()
    }

    override suspend fun findByExternalId(externalId: ExternalBetId): Bet? = dbRead {
        BetEntity.find { BetTable.externalId eq externalId.value }
            .with(*betChain)
            .firstOrNull()?.toDomain()
    }

    override suspend fun deleteById(id: Long) {
        dbTransaction {
            BetTable.deleteWhere { BetTable.id eq id }
        }
    }

    private fun UpdateBuilder<*>.fromDomain(bet: Bet) {
        this[BetTable.externalId] = bet.externalId.value
        this[BetTable.playerId] = bet.playerId.value
        this[BetTable.session] = bet.session.id
        this[BetTable.currency] = bet.currency.value
        this[BetTable.betAmount] = bet.betAmount.value
        this[BetTable.winAmount] = bet.winAmount.value
        this[BetTable.type] = bet.type
        this[BetTable.status] = bet.status
        this[BetTable.selections] = bet.selections
        this[BetTable.createdAt] = bet.createdAt
        this[BetTable.updatedAt] = bet.updatedAt
    }
}
