package infrastructure.persistence.repository

import domain.model.Freespin
import domain.repository.IFreespinRepository
import domain.vo.FreespinId
import domain.vo.PlayerId
import infrastructure.persistence.dbRead
import infrastructure.persistence.dbTransaction
import infrastructure.persistence.entity.FreespinEntity
import infrastructure.persistence.entity.CasinoGameEntity
import infrastructure.persistence.entity.CasinoGameVariantEntity
import infrastructure.persistence.entity.CasinoProviderEntity
import infrastructure.persistence.mapper.FreespinMapper.toDomain
import infrastructure.persistence.table.FreespinTable
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.update

class FreespinRepositoryImpl : IFreespinRepository {

    private val variantChain = arrayOf(
        FreespinEntity::gameVariant,
        CasinoGameVariantEntity::game,
        CasinoGameEntity::provider,
        CasinoGameEntity::collections,
        CasinoProviderEntity::aggregator,
    )

    override suspend fun save(freespin: Freespin): Freespin = dbTransaction {
        if (freespin.id == Long.MIN_VALUE) {
            val id = FreespinTable.insertAndGetId { it.fromDomain(freespin) }
            freespin.copy(id = id.value)
        } else {
            FreespinTable.update({ FreespinTable.id eq freespin.id }) { it.fromDomain(freespin) }
            freespin
        }
    }

    override suspend fun findByReferenceId(referenceId: FreespinId): Freespin? = dbRead {
        FreespinEntity.find { FreespinTable.referenceId eq referenceId.value }
            .with(*variantChain)
            .firstOrNull()?.toDomain()
    }

    override suspend fun findRedeemable(
        playerId: PlayerId,
        gameVariantId: Long,
        now: Instant,
    ): Freespin? = dbRead {
        FreespinEntity.find {
            (FreespinTable.playerId eq playerId.value) and
                (FreespinTable.gameVariant eq gameVariantId) and
                (FreespinTable.cancelledAt.isNull()) and
                (FreespinTable.remainingCount greater 0) and
                (FreespinTable.startAt lessEq now) and
                (FreespinTable.endAt greater now)
        }
            .with(*variantChain)
            // Oldest first: a grant that expires sooner should be spent before a fresher one.
            .minByOrNull { it.endAt }?.toDomain()
    }

    private fun UpdateBuilder<*>.fromDomain(freespin: Freespin) {
        this[FreespinTable.referenceId] = freespin.referenceId.value
        this[FreespinTable.playerId] = freespin.playerId.value
        this[FreespinTable.gameVariant] = freespin.gameVariant.id
        this[FreespinTable.currency] = freespin.currency.value
        this[FreespinTable.spinAmount] = freespin.spinAmount.value
        this[FreespinTable.totalCount] = freespin.totalCount
        this[FreespinTable.remainingCount] = freespin.remainingCount
        this[FreespinTable.startAt] = freespin.startAt
        this[FreespinTable.endAt] = freespin.endAt
        this[FreespinTable.cancelledAt] = freespin.cancelledAt
        this[FreespinTable.createdAt] = freespin.createdAt
    }
}
