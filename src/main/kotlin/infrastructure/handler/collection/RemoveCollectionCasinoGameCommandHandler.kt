package infrastructure.handler.collection

import application.ICommandHandler
import application.command.collection.RemoveCollectionCasinoGameCommand
import domain.repository.ICollectionRepository

class RemoveCollectionCasinoGameCommandHandler(
    private val collectionRepository: ICollectionRepository,
) : ICommandHandler<RemoveCollectionCasinoGameCommand, Unit> {

    override suspend fun handle(command: RemoveCollectionCasinoGameCommand): Result<Unit> = runCatching {
        collectionRepository.removeCasinoGame(command.identity, command.gameIdentity)
    }
}
