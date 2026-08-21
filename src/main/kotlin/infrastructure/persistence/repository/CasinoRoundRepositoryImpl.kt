package infrastructure.persistence.repository

import domain.model.CasinoRound
import domain.repository.ICasinoRoundRepository
import domain.vo.ExternalCasinoRoundId
import infrastructure.persistence.dbRead
import infrastructure.persistence.dbTransaction
import infrastructure.persistence.entity.CasinoGameEntity
import infrastructure.persistence.entity.CasinoGameVariantEntity
import infrastructure.persistence.entity.CasinoProviderEntity
import infrastructure.persistence.entity.CasinoRoundEntity
import infrastructure.persistence.entity.CasinoSessionEntity
import infrastructure.persistence.mapper.CasinoRoundMapper.toDomain
import infrastructure.persistence.table.CasinoRoundTable
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.update

class CasinoRoundRepositoryImpl : ICasinoRoundRepository {

    private val roundChain = arrayOf(
        CasinoRoundEntity::session,
        CasinoRoundEntity::gameVariant,
        CasinoSessionEntity::gameVariant,
        CasinoGameVariantEntity::game,
        CasinoGameEntity::provider,
        CasinoGameEntity::collections,
        CasinoProviderEntity::aggregator,
    )

    override suspend fun save(round: CasinoRound): CasinoRound = dbTransaction {
        if (round.id == Long.MIN_VALUE) {
            val id = CasinoRoundTable.insertAndGetId { it.fromDomain(round) }
            round.copy(id = id.value)
        } else {
            CasinoRoundTable.update({ CasinoRoundTable.id eq round.id }) { it.fromDomain(round) }
            round
        }
    }

    override suspend fun findById(id: Long): CasinoRound? = dbRead {
        CasinoRoundEntity.findById(id)
            ?.load(*roundChain)
            ?.toDomain()
    }

    override suspend fun findByExternalIdAndSessionId(externalId: ExternalCasinoRoundId, sessionId: Long): CasinoRound? = dbRead {
        CasinoRoundEntity.find { (CasinoRoundTable.externalId eq externalId.value) and (CasinoRoundTable.session eq sessionId) }
            .with(*roundChain)
            .firstOrNull()?.toDomain()
    }

    private fun UpdateBuilder<*>.fromDomain(round: CasinoRound) {
        this[CasinoRoundTable.externalId] = round.externalId.value
        this[CasinoRoundTable.freespinId] = round.freespinId?.value
        this[CasinoRoundTable.session] = round.session.id
        this[CasinoRoundTable.gameVariant] = round.gameVariant.id
        this[CasinoRoundTable.createdAt] = round.createdAt
        this[CasinoRoundTable.finishedAt] = round.finishedAt
    }
}
