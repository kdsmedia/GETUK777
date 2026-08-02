package domain.repository

import domain.model.Spin
import kotlinx.datetime.Instant

interface ISpinRepository {

    suspend fun save(spin: Spin): Spin

    suspend fun findById(id: Long): Spin?

    suspend fun findByExternalId(externalId: String): Spin?

    suspend fun findAllSince(since: Instant): List<Spin>

}
