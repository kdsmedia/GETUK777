package domain.repository

import domain.model.Aggregator
import domain.model.AggregatorType
import domain.vo.Identity

interface IAggregatorRepository {

    suspend fun save(aggregator: Aggregator): Aggregator

    suspend fun findByIdentity(identity: Identity): Aggregator?

    /** Resolves the aggregator that actually serves a variant. `integration` is unique across
     *  aggregators, so a variant's integration string names exactly one of them. */
    suspend fun findByIntegration(integration: String): Aggregator?

    suspend fun findFirstActiveByType(type: AggregatorType): Aggregator?

    suspend fun findAllByIdentities(identities: List<Identity>): List<Aggregator>

    suspend fun findAll(): List<Aggregator>

    suspend fun deleteByIdentity(identity: Identity)

}
