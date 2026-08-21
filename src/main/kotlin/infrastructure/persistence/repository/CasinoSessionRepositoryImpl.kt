package infrastructure.persistence.repository

import domain.repository.ICasinoSessionRepository
import domain.model.CasinoSession
import infrastructure.persistence.dbRead
import infrastructure.persistence.dbTransaction
import infrastructure.persistence.entity.CasinoGameEntity
import infrastructure.persistence.entity.CasinoGameVariantEntity
import infrastructure.persistence.entity.CasinoProviderEntity
import infrastructure.persistence.entity.CasinoSessionEntity
import infrastructure.persistence.mapper.CasinoSessionMapper.toDomain
import infrastructure.persistence.table.CasinoSessionTable
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.update

class CasinoSessionRepositoryImpl : ICasinoSessionRepository {

    override suspend fun save(session: CasinoSession): CasinoSession = dbTransaction {
        if (session.id == Long.MIN_VALUE) {
            val id = CasinoSessionTable.insertAndGetId { it.fromDomain(session) }
            session.copy(id = id.value)
        } else {
            CasinoSessionTable.update({ CasinoSessionTable.id eq session.id }) { it.fromDomain(session) }
            session
        }
    }

    override suspend fun findById(id: Long): CasinoSession? = dbRead {
        CasinoSessionEntity.findById(id)
            ?.load(
                CasinoSessionEntity::gameVariant,
                CasinoGameVariantEntity::game,
                CasinoGameEntity::provider,
                CasinoGameEntity::collections,
                CasinoProviderEntity::aggregator,
            )
            ?.toDomain()
    }

    override suspend fun findByToken(token: String): CasinoSession? = dbRead {
        CasinoSessionEntity.find { CasinoSessionTable.token eq token }
            .with(
                CasinoSessionEntity::gameVariant,
                CasinoGameVariantEntity::game,
                CasinoGameEntity::provider,
                CasinoGameEntity::collections,
                CasinoProviderEntity::aggregator,
            )
            .firstOrNull()?.toDomain()
    }

    override suspend fun findByExternalToken(externalToken: String): CasinoSession? = dbRead {
        CasinoSessionEntity.find { CasinoSessionTable.externalToken eq externalToken }
            .with(
                CasinoSessionEntity::gameVariant,
                CasinoGameVariantEntity::game,
                CasinoGameEntity::provider,
                CasinoGameEntity::collections,
                CasinoProviderEntity::aggregator,
            )
            // Newest wins: a provider id is unique in practice, but nothing in the schema enforces it.
            .maxByOrNull { it.id.value }?.toDomain()
    }

    private fun UpdateBuilder<*>.fromDomain(session: CasinoSession) {
        this[CasinoSessionTable.gameVariant] = session.gameVariant.id
        this[CasinoSessionTable.playerId] = session.playerId.value
        this[CasinoSessionTable.token] = session.token.value
        this[CasinoSessionTable.externalToken] = session.externalToken
        this[CasinoSessionTable.currency] = session.currency.value
        this[CasinoSessionTable.locale] = session.locale.value
        this[CasinoSessionTable.platform] = session.platform
    }
}
