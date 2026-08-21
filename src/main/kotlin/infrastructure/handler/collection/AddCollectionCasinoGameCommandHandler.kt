package infrastructure.handler.collection

import application.ICommandHandler
import application.command.collection.AddCollectionCasinoGameCommand
import domain.repository.ICollectionRepository

class AddCollectionCasinoGameCommandHandler(
    private val collectionRepository: ICollectionRepository,
) : ICommandHandler<AddCollectionCasinoGameCommand, Unit> {

    override suspend fun handle(command: AddCollectionCasinoGameCommand): Result<Unit> = runCatching {
        collectionRepository.addCasinoGame(command.identity, command.gameIdentity)
    }
}
