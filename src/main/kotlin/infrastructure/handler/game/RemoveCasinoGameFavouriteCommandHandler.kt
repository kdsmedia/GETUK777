package infrastructure.handler.game

import application.ICommandHandler
import application.command.game.RemoveCasinoGameFavouriteCommand
import domain.exception.domainRequireNotNull
import domain.exception.notfound.CasinoGameNotFoundException
import infrastructure.persistence.dbTransaction
import infrastructure.persistence.table.CasinoGameFavouriteTable
import infrastructure.persistence.table.CasinoGameTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere

class RemoveCasinoGameFavouriteCommandHandler : ICommandHandler<RemoveCasinoGameFavouriteCommand, Unit> {

    override suspend fun handle(command: RemoveCasinoGameFavouriteCommand): Result<Unit> = runCatching {
        dbTransaction {
            val gameId = domainRequireNotNull(
                CasinoGameTable.select(CasinoGameTable.id)
                    .where { CasinoGameTable.identity eq command.identity.value }
                    .singleOrNull()?.get(CasinoGameTable.id)
            ) { CasinoGameNotFoundException() }

            CasinoGameFavouriteTable.deleteWhere {
                (game eq gameId) and (playerId eq command.playerId.value)
            }
        }
    }
}
