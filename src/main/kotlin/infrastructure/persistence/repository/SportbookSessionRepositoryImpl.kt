package infrastructure.persistence.repository

import domain.exception.domainRequireNotNull
import domain.exception.notfound.AggregatorNotFoundException
import domain.model.SportbookSession
import domain.repository.ISportbookSessionRepository
import domain.vo.PlayerId
import domain.vo.SportbookSessionToken
import infrastructure.persistence.dbRead
import infrastructure.persistence.dbTransaction
import infrastructure.persistence.entity.SportbookSessionEntity
import infrastructure.persistence.mapper.SportbookSessionMapper.toDomain
import infrastructure.persistence.table.AggregatorTable
import infrastructure.persistence.table.SportbookSessionTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.update

class SportbookSessionRepositoryImpl : ISportbookSessionRepository {

    override suspend fun save(session: SportbookSession): SportbookSession = dbTransaction {
        val aggregatorId = domainRequireNotNull(
            AggregatorTable.select(AggregatorTable.id)
                .where { AggregatorTable.identity eq session.aggregator.identity.value }
                .singleOrNull()?.get(AggregatorTable.id)
        ) { AggregatorNotFoundException() }

        if (session.id == Long.MIN_VALUE) {
            val id = SportbookSessionTable.insertAndGetId { it.fromDomain(session, aggregatorId) }
            session.copy(id = id.value)
        } else {
            SportbookSessionTable.update({ SportbookSessionTable.id eq session.id }) {
                it.fromDomain(session, aggregatorId)
            }
            session
        }
    }

    override suspend fun findById(id: Long): SportbookSession? = dbRead {
        SportbookSessionEntity.findById(id)
            ?.load(SportbookSessionEntity::aggregator)
            ?.toDomain()
    }

    override suspend fun findByToken(token: SportbookSessionToken): SportbookSession? = dbRead {
        SportbookSessionEntity.find { SportbookSessionTable.token eq token.value }
            .with(SportbookSessionEntity::aggregator)
            .firstOrNull()?.toDomain()
    }

    override suspend fun findByExternalToken(externalToken: String): SportbookSession? = dbRead {
        SportbookSessionEntity.find { SportbookSessionTable.externalToken eq externalToken }
            .with(SportbookSessionEntity::aggregator)
            .firstOrNull()?.toDomain()
    }

    override suspend fun findLastByPlayerId(playerId: PlayerId): SportbookSession? = dbRead {
        SportbookSessionEntity.find { SportbookSessionTable.playerId eq playerId.value }
            .orderBy(SportbookSessionTable.id to SortOrder.DESC)
            .limit(1)
            .with(SportbookSessionEntity::aggregator)
            .firstOrNull()?.toDomain()
    }

    private fun UpdateBuilder<*>.fromDomain(
        session: SportbookSession,
        aggregatorId: EntityID<Long>,
    ) {
        this[SportbookSessionTable.token] = session.token.value
        this[SportbookSessionTable.externalToken] = session.externalToken
        this[SportbookSessionTable.playerId] = session.playerId.value
        this[SportbookSessionTable.currency] = session.currency.value
        this[SportbookSessionTable.aggregator] = aggregatorId
        this[SportbookSessionTable.data] = session.data
        this[SportbookSessionTable.createdAt] = session.createdAt
    }
}
