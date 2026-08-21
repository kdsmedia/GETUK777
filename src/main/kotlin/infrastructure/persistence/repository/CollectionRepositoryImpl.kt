package infrastructure.persistence.repository

import domain.exception.domainRequireNotNull
import domain.exception.notfound.CollectionNotFoundException
import domain.exception.notfound.CasinoGameNotFoundException
import domain.model.Collection
import domain.repository.ICollectionRepository
import domain.vo.Identity
import domain.vo.Page
import domain.vo.Pageable
import infrastructure.persistence.dbRead
import infrastructure.persistence.dbTransaction
import infrastructure.persistence.entity.CollectionEntity
import infrastructure.persistence.mapper.CollectionMapper.toCollection
import infrastructure.persistence.table.CollectionTable
import infrastructure.persistence.table.CasinoGameCollectionTable
import infrastructure.persistence.table.CasinoGameTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert

class CollectionRepositoryImpl : ICollectionRepository {

    override suspend fun save(collection: Collection): Collection = dbTransaction {
        CollectionTable.upsert(keys = arrayOf(CollectionTable.identity)) {
            it[identity] = collection.identity.value
            it[name] = collection.name.data
            it[tags] = collection.tags
            it[images] = collection.images.data
            it[active] = collection.active
            it[sortOrder] = collection.order
        }

        collection
    }

    override suspend fun findByIdentity(identity: Identity): Collection? = dbRead {
        CollectionTable
            .selectAll()
            .where { CollectionTable.identity eq identity.value }
            .singleOrNull()
            ?.toCollection()
    }

    override suspend fun findAll(pageable: Pageable): Page<Collection> = dbRead {
        val totalItems = CollectionTable.selectAll().count()
        val items = CollectionTable
            .selectAll()
            .orderBy(CollectionTable.sortOrder to SortOrder.ASC)
            .limit(pageable.sizeReal)
            .offset(pageable.offset)
            .map { it.toCollection() }

        Page(
            items = items,
            totalPages = pageable.getTotalPages(totalItems),
            totalItems = totalItems,
            currentPage = pageable.pageReal,
        )
    }

    override suspend fun addImage(identity: Identity, key: String, url: String) {
        dbTransaction {
            val entity = domainRequireNotNull(
                CollectionEntity.find { CollectionTable.identity eq identity.value }.firstOrNull()
            ) { CollectionNotFoundException() }
            entity.images = entity.images.toMutableMap().apply { put(key, url) }
        }
    }

    override suspend fun addCasinoGame(identity: Identity, gameIdentity: Identity) {
        dbTransaction {
            val collectionId = resolveCollectionId(identity)
            val gameId = resolveGameId(gameIdentity)

            val alreadyMember = CasinoGameCollectionTable
                .selectAll()
                .where { (CasinoGameCollectionTable.collection eq collectionId) and (CasinoGameCollectionTable.game eq gameId) }
                .any()
            if (alreadyMember) return@dbTransaction

            val maxOrderExpr = CasinoGameCollectionTable.sortOrder.max()
            val currentMaxOrder = CasinoGameCollectionTable
                .select(maxOrderExpr)
                .where { CasinoGameCollectionTable.collection eq collectionId }
                .singleOrNull()
                ?.get(maxOrderExpr)
            val nextOrder = if (currentMaxOrder == null) 0 else currentMaxOrder + 1

            CasinoGameCollectionTable.insert {
                it[collection] = collectionId
                it[game] = gameId
                it[sortOrder] = nextOrder
            }
        }
    }

    override suspend fun removeCasinoGame(identity: Identity, gameIdentity: Identity) {
        dbTransaction {
            val collectionId = resolveCollectionId(identity)
            val gameId = resolveGameId(gameIdentity)

            CasinoGameCollectionTable.deleteWhere {
                (collection eq collectionId) and (game eq gameId)
            }
        }
    }

    override suspend fun updateCasinoGameOrder(identity: Identity, gameIdentity: Identity, order: Int) {
        dbTransaction {
            val collectionId = resolveCollectionId(identity)
            val gameId = resolveGameId(gameIdentity)

            val affected = CasinoGameCollectionTable.update(
                { (CasinoGameCollectionTable.collection eq collectionId) and (CasinoGameCollectionTable.game eq gameId) }
            ) {
                it[sortOrder] = order
            }

            // Zero rows touched → the game isn't actually a member of this collection.
            domainRequireNotNull(if (affected > 0) Unit else null) { CasinoGameNotFoundException() }
        }
    }

    override suspend fun deleteByIdentity(identity: Identity) {
        dbTransaction {
            val collectionId = resolveCollectionId(identity)

            // Memberships first — game_collections references collections, and the
            // games on the other side of those rows must survive the delete.
            CasinoGameCollectionTable.deleteWhere { CasinoGameCollectionTable.collection eq collectionId }
            CollectionTable.deleteWhere { CollectionTable.id eq collectionId }
        }
    }

    private fun resolveCollectionId(identity: Identity) = domainRequireNotNull(
        CollectionTable
            .select(CollectionTable.id)
            .where { CollectionTable.identity eq identity.value }
            .singleOrNull()
            ?.get(CollectionTable.id)
    ) { CollectionNotFoundException() }

    private fun resolveGameId(gameIdentity: Identity) = domainRequireNotNull(
        CasinoGameTable
            .select(CasinoGameTable.id)
            .where { CasinoGameTable.identity eq gameIdentity.value }
            .singleOrNull()
            ?.get(CasinoGameTable.id)
    ) { CasinoGameNotFoundException() }
}
