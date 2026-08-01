package infrastructure.handler.winner

import application.IQueryHandler
import application.query.winner.LastWin
import application.query.winner.LastWinnerQuery
import application.query.winner.WinnerSort
import domain.model.Game
import domain.model.GameVariant
import domain.model.Platform
import domain.model.SpinType
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.GameSymbol
import domain.vo.Locale
import domain.vo.Page
import domain.vo.PlayerId
import infrastructure.handler.game.toCondition
import infrastructure.persistence.dbRead
import infrastructure.persistence.mapper.GameMapper.toGame
import infrastructure.persistence.table.AggregatorTable
import infrastructure.persistence.table.GameTable
import infrastructure.persistence.table.GameVariantTable
import infrastructure.persistence.table.ProviderTable
import infrastructure.persistence.table.RoundTable
import infrastructure.persistence.table.SessionTable
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
            .innerJoin(RoundTable)
            .join(SessionTable, JoinType.INNER, RoundTable.session, SessionTable.id)
            .join(GameVariantTable, JoinType.INNER, RoundTable.gameVariant, GameVariantTable.id)
            .join(GameTable, JoinType.INNER, GameVariantTable.game, GameTable.id)
            .join(ProviderTable, JoinType.INNER, GameTable.provider, ProviderTable.id)
            .join(AggregatorTable, JoinType.INNER, ProviderTable.aggregator, AggregatorTable.id)
            .select(
                SpinTable.amount,
                RoundTable.createdAt,
                SessionTable.currency,
                SessionTable.playerId,
                GameTable.identity,
                GameTable.name,
                GameTable.bonusBetEnable,
                GameTable.bonusWageringEnable,
                GameTable.tags,
                GameTable.active,
                GameTable.images,
                GameTable.sortOrder,
                // Список колонок обязан покрывать ВСЁ, что читают row-мапперы
                // (GameMapper.toGame -> ProviderMapper.toProvider -> toAggregator):
                // недостающая колонка роняет запрос в рантайме на первой же строке,
                // а на пустой выдаче маппер не вызывается и баг не виден.
                ProviderTable.identity,
                ProviderTable.name,
                ProviderTable.images,
                ProviderTable.sortOrder,
                ProviderTable.active,
                ProviderTable.blockedCountry,
                ProviderTable.tags,
                AggregatorTable.identity,
                AggregatorTable.integration,
                AggregatorTable.config,
                AggregatorTable.active,
                GameVariantTable.id,
                GameVariantTable.symbol,
                GameVariantTable.name,
                GameVariantTable.integration,
                GameVariantTable.providerName,
                GameVariantTable.freeSpinEnable,
                GameVariantTable.freeChipEnable,
                GameVariantTable.jackpotEnable,
                GameVariantTable.demoEnable,
                GameVariantTable.bonusBuyEnable,
                GameVariantTable.locales,
                GameVariantTable.platforms,
                GameVariantTable.playLines,
            )
            .where {
                (SpinTable.type eq SpinType.SETTLE) and (RoundTable.freespinId.isNull())
            }

        // Тот же предикат, что и у листингов игр: провайдер/коллекция/теги/флаги.
        // Условия по варианту он вешает коррелированным EXISTS, поэтому уже
        // присоединённый GameVariantTable ему не мешает.
        query.filter?.let { filter -> baseQuery.andWhere { filter.toCondition() } }
        query.minAmount?.let { baseQuery.andWhere { SpinTable.amount greaterEq it.value } }
        query.maxAmount?.let { baseQuery.andWhere { SpinTable.amount lessEq it.value } }
        query.currency?.let { baseQuery.andWhere { SessionTable.currency eq it.value } }
        query.playerId?.let { baseQuery.andWhere { SessionTable.playerId eq it.value } }
        query.fromDate?.let { baseQuery.andWhere { RoundTable.createdAt greaterEq it } }
        query.toDate?.let { baseQuery.andWhere { RoundTable.createdAt lessEq it } }

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
                RoundTable.createdAt to SortOrder.DESC,
                SpinTable.id to SortOrder.DESC,
            )
        }

        val rows = baseQuery
            .orderBy(*ordering)
            .limit(pageable.sizeReal)
            .offset(pageable.offset)
            .toList()

        val items = rows.map { row ->
            val game = row.toGame()
            LastWin(
                game = game,
                variant = row.toGameVariant(game),
                amount = Amount(row[SpinTable.amount]),
                currency = Currency(row[SessionTable.currency]),
                playerId = PlayerId(row[SessionTable.playerId]),
                date = row[RoundTable.createdAt],
            )
        }

        Page(
            items = items,
            totalPages = pageable.getTotalPages(totalItems),
            totalItems = totalItems,
            currentPage = pageable.pageReal,
        )
    }

    private fun ResultRow.toGameVariant(game: Game): GameVariant = GameVariant(
        id = this[GameVariantTable.id].value,
        symbol = GameSymbol(this[GameVariantTable.symbol]),
        name = this[GameVariantTable.name],
        integration = this[GameVariantTable.integration],
        game = game,
        providerName = this[GameVariantTable.providerName],
        freeSpinEnable = this[GameVariantTable.freeSpinEnable],
        freeChipEnable = this[GameVariantTable.freeChipEnable],
        jackpotEnable = this[GameVariantTable.jackpotEnable],
        demoEnable = this[GameVariantTable.demoEnable],
        bonusBuyEnable = this[GameVariantTable.bonusBuyEnable],
        locales = this[GameVariantTable.locales].map { Locale(it) },
        platforms = this[GameVariantTable.platforms].map { Platform.valueOf(it) },
        playLines = this[GameVariantTable.playLines],
    )
}
