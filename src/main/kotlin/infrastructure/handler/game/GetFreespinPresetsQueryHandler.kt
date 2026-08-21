package infrastructure.handler.game

import application.IQueryHandler
import application.query.freespin.GetFreespinPresetsQuery
import application.port.factory.IAggregatorFactory
import domain.exception.conflict.FreespinNotSupportedException
import domain.exception.notfound.CasinoGameNotFoundException
import infrastructure.persistence.mapper.AggregatorMapper.toAggregator
import infrastructure.persistence.table.AggregatorTable
import infrastructure.persistence.table.CasinoGameTable
import infrastructure.persistence.table.CasinoGameVariantTable
import infrastructure.persistence.table.CasinoProviderTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import infrastructure.persistence.dbRead

class GetFreespinPresetsQueryHandler(
    private val aggregatorFactory: IAggregatorFactory
) : IQueryHandler<GetFreespinPresetsQuery, Map<String, Any>> {

    override suspend fun handle(query: GetFreespinPresetsQuery): Map<String, Any> {
        val (aggregator, variantSymbol) = dbRead {
            val row = CasinoGameVariantTable
                .join(CasinoGameTable, JoinType.INNER, CasinoGameVariantTable.game, CasinoGameTable.id)
                .join(CasinoProviderTable, JoinType.INNER, CasinoGameTable.provider, CasinoProviderTable.id)
                .join(AggregatorTable, JoinType.INNER, CasinoProviderTable.aggregator, AggregatorTable.id)
                .selectAll()
                .where {
                    (CasinoGameTable.identity eq query.gameIdentity.value) and
                            (CasinoGameVariantTable.integration eq AggregatorTable.integration)
                }
                .firstOrNull() ?: throw CasinoGameNotFoundException()

            if (!row[CasinoGameVariantTable.freeSpinEnable]) {
                throw FreespinNotSupportedException()
            }

            row.toAggregator() to row[CasinoGameVariantTable.symbol]
        }

        val freespinAdapter = aggregatorFactory.createFreespinAdapter(aggregator)

        return freespinAdapter.getPreset(variantSymbol)
    }
}
