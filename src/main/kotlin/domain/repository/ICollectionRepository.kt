package domain.repository

import domain.model.Collection
import domain.vo.Identity
import domain.vo.Page
import domain.vo.Pageable

interface ICollectionRepository {

    suspend fun save(collection: Collection): Collection

    suspend fun findByIdentity(identity: Identity): Collection?

    suspend fun findAll(pageable: Pageable): Page<Collection>

    suspend fun addImage(identity: Identity, key: String, url: String)

    /**
     * Delete the collection and every game membership it holds. Games themselves
     * are never touched — only the rail and its ordering disappear.
     *
     * Raises `CollectionNotFoundException` if [identity] does not exist.
     */
    suspend fun deleteByIdentity(identity: Identity)

    /**
     * Add a single game to a collection. Idempotent: if [gameIdentity] is
     * already in [identity], no-op. On first insert, sort order is set to
     * `max(existing sort_order) + 1` (or `0` when the collection is empty).
     *
     * Raises `CollectionNotFoundException` if [identity] does not exist, or
     * `CasinoGameNotFoundException` if [gameIdentity] does not exist.
     */
    suspend fun addCasinoGame(identity: Identity, gameIdentity: Identity)

    /**
     * Remove a single game from a collection. Idempotent: if [gameIdentity]
     * is not currently a member, no-op.
     *
     * Raises `CollectionNotFoundException` if [identity] does not exist, or
     * `CasinoGameNotFoundException` if [gameIdentity] does not exist.
     */
    suspend fun removeCasinoGame(identity: Identity, gameIdentity: Identity)

    /**
     * Set the per-collection [order] of [gameIdentity] inside [identity].
     *
     * Raises `CollectionNotFoundException` if [identity] does not exist, or
     * `CasinoGameNotFoundException` if the (collection, game) row does not exist.
     */
    suspend fun updateCasinoGameOrder(identity: Identity, gameIdentity: Identity, order: Int)

}
