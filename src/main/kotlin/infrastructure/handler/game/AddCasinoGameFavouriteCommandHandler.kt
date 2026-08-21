package infrastructure.handler.game

import application.ICommandHandler
import application.command.game.AddCasinoGameFavouriteCommand
import domain.exception.domainRequireNotNull
import domain.exception.notfound.CasinoGameNotFoundException
import infrastructure.persistence.dbTransaction
import infrastructure.persistence.table.CasinoGameFavouriteTable
import infrastructure.persistence.table.CasinoGameTable
import org.jetbrains.exposed.sql.insertIgnore

class AddCasinoGameFavouriteCommandHandler : ICommandHandler<AddCasinoGameFavouriteCommand, Unit> {

    override suspend fun handle(command: AddCasinoGameFavouriteCommand): Result<Unit> = runCatching {
        dbTransaction {
            val gameId = domainRequireNotNull(
                CasinoGameTable.select(CasinoGameTable.id)
                    .where { CasinoGameTable.identity eq command.identity.value }
                    .singleOrNull()?.get(CasinoGameTable.id)
            ) { CasinoGameNotFoundException() }

            CasinoGameFavouriteTable.insertIgnore {
                it[game] = gameId
                it[playerId] = command.playerId.value
            }
        }
    }
}
