package infrastructure.handler.winner

import application.IQueryHandler
import application.query.winner.LastWin
import application.query.winner.LastWinnerQuery
import application.query.winner.WinnerSort
import domain.model.CasinoGame
import domain.model.CasinoGameVariant
import domain.model.Platform
import domain.model.SpinType
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.CasinoGameSymbol
import domain.vo.Locale
import domain.vo.Page
import domain.vo.PlayerId
import infrastructure.handler.game.toCondition
import infrastructure.persistence.dbRead
import infrastructure.persistence.mapper.CasinoGameMapper.toCasinoGame
import infrastructure.persistence.table.AggregatorTable
import infrastructure.persistence.table.CasinoGameTable
import infrastructure.persistence.table.CasinoGameVariantTable
import infrastructure.persistence.table.CasinoProviderTable
import infrastructure.persistence.table.CasinoRoundTable
import infrastructure.persistence.table.CasinoSessionTable
import infrastructure.persistence.table.SpinTable
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere

class LastWinnerQueryHandler : IQueryHandler<LastWinnerQuery, Page<LastWin>> {

    override suspend fun handle(query: LastWinnerQuery): Page<LastWin> = dbRead {
        val baseQuery = SpinTable
            .innerJoin(CasinoRoundTable)
            .join(CasinoSessionTable, JoinType.INNER, CasinoRoundTable.session, CasinoSessionTable.id)
            .join(CasinoGameVariantTable, JoinType.INNER, CasinoRoundTable.gameVariant, CasinoGameVariantTable.id)
            .join(CasinoGameTable, JoinType.INNER, CasinoGameVariantTable.game, CasinoGameTable.id)
            .join(CasinoProviderTable, JoinType.INNER, CasinoGameTable.provider, CasinoProviderTable.id)
            .join(AggregatorTable, JoinType.INNER, CasinoProviderTable.aggregator, AggregatorTable.id)
            .select(
                SpinTable.amount,
                CasinoRoundTable.createdAt,
                CasinoSessionTable.currency,
                CasinoSessionTable.playerId,
                CasinoGameTable.identity,
                CasinoGameTable.name,
                CasinoGameTable.bonusBetEnable,
                CasinoGameTable.bonusWageringEnable,
                CasinoGameTable.tags,
                CasinoGameTable.active,
                CasinoGameTable.images,
                CasinoGameTable.sortOrder,
                CasinoGameTable.rtp,
                // Список колонок обязан покрывать ВСЁ, что читают row-мапперы
                // (CasinoGameMapper.toCasinoGame -> CasinoProviderMapper.toCasinoProvider -> toAggregator):
                // недостающая колонка роняет запрос в рантайме на первой же строке,
                // а на пустой выдаче маппер не вызывается и баг не виден.
                CasinoProviderTable.identity,
                CasinoProviderTable.name,
                CasinoProviderTable.images,
                CasinoProviderTable.sortOrder,
                CasinoProviderTable.active,
                CasinoProviderTable.blockedCountry,
                CasinoProviderTable.tags,
                AggregatorTable.identity,
                AggregatorTable.integration,
                AggregatorTable.config,
                AggregatorTable.active,
                CasinoGameVariantTable.id,
                CasinoGameVariantTable.symbol,
                CasinoGameVariantTable.name,
                CasinoGameVariantTable.integration,
                CasinoGameVariantTable.providerName,
                CasinoGameVariantTable.freeSpinEnable,
                CasinoGameVariantTable.freeChipEnable,
                CasinoGameVariantTable.jackpotEnable,
                CasinoGameVariantTable.demoEnable,
                CasinoGameVariantTable.bonusBuyEnable,
                CasinoGameVariantTable.locales,
                CasinoGameVariantTable.platforms,
                CasinoGameVariantTable.playLines,
            )
            .where {
                // A lost round settles as a zero-amount SETTLE — it is a settlement, not a win,
                // and listing it puts "0" rows in the player-facing winners feed.
                (SpinTable.type eq SpinType.SETTLE) and
                    (SpinTable.amount greater 0L) and
                    (CasinoRoundTable.freespinId.isNull())
            }

        // Тот же предикат, что и у листингов игр: провайдер/коллекция/теги/флаги.
        // Условия по варианту он вешает коррелированным EXISTS, поэтому уже
        // присоединённый CasinoGameVariantTable ему не мешает.
        query.filter?.let { filter -> baseQuery.andWhere { filter.toCondition() } }
        query.minAmount?.let { baseQuery.andWhere { SpinTable.amount greaterEq it.value } }
        query.maxAmount?.let { baseQuery.andWhere { SpinTable.amount lessEq it.value } }
        query.currency?.let { baseQuery.andWhere { CasinoSessionTable.currency eq it.value } }
        query.playerId?.let { baseQuery.andWhere { CasinoSessionTable.playerId eq it.value } }
        query.fromDate?.let { baseQuery.andWhere { CasinoRoundTable.createdAt greaterEq it } }
        query.toDate?.let { baseQuery.andWhere { CasinoRoundTable.createdAt lessEq it } }

        val totalItems = baseQuery.count()
        val pageable = query.pageable

        // Всегда по убыванию. Хвостовой ключ — id спина: createdAt и amount не
        // уникальны, а на равных ключах страницы «плывут» (строка повторяется
        // на следующей странице или пропадает).
        val ordering: Array<Pair<Expression<*>, SortOrder>> = when (query.sort) {
            WinnerSort.AMOUNT -> arrayOf(
                SpinTable.amount to SortOrder.DESC,
                SpinTable.id to SortOrder.DESC,
            )

            WinnerSort.DATE -> arrayOf(
                CasinoRoundTable.createdAt to SortOrder.DESC,
                SpinTable.id to SortOrder.DESC,
            )
        }

        val rows = baseQuery
            .orderBy(*ordering)
            .limit(pageable.sizeReal)
            .offset(pageable.offset)
            .toList()

        val items = rows.map { row ->
            val game = row.toCasinoGame()
            LastWin(
                game = game,
                variant = row.toCasinoGameVariant(game),
                amount = Amount(row[SpinTable.amount]),
                currency = Currency(row[CasinoSessionTable.currency]),
                playerId = PlayerId(row[CasinoSessionTable.playerId]),
                date = row[CasinoRoundTable.createdAt],
            )
        }

        Page(
            items = items,
            totalPages = pageable.getTotalPages(totalItems),
            totalItems = totalItems,
            currentPage = pageable.pageReal,
        )
    }

    private fun ResultRow.toCasinoGameVariant(game: CasinoGame): CasinoGameVariant = CasinoGameVariant(
        id = this[CasinoGameVariantTable.id].value,
        symbol = CasinoGameSymbol(this[CasinoGameVariantTable.symbol]),
        name = this[CasinoGameVariantTable.name],
        integration = this[CasinoGameVariantTable.integration],
        game = game,
        providerName = this[CasinoGameVariantTable.providerName],
        freeSpinEnable = this[CasinoGameVariantTable.freeSpinEnable],
        freeChipEnable = this[CasinoGameVariantTable.freeChipEnable],
        jackpotEnable = this[CasinoGameVariantTable.jackpotEnable],
        demoEnable = this[CasinoGameVariantTable.demoEnable],
        bonusBuyEnable = this[CasinoGameVariantTable.bonusBuyEnable],
        locales = this[CasinoGameVariantTable.locales].map { Locale(it) },
        platforms = this[CasinoGameVariantTable.platforms].map { Platform.valueOf(it) },
        playLines = this[CasinoGameVariantTable.playLines],
    )
}
