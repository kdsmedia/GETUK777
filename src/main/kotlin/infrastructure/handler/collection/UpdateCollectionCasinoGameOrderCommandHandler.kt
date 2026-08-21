package infrastructure.handler.collection

import application.ICommandHandler
import application.command.collection.UpdateCollectionCasinoGameOrderCommand
import domain.repository.ICollectionRepository

class UpdateCollectionCasinoGameOrderCommandHandler(
    private val collectionRepository: ICollectionRepository,
) : ICommandHandler<UpdateCollectionCasinoGameOrderCommand, Unit> {

    override suspend fun handle(command: UpdateCollectionCasinoGameOrderCommand): Result<Unit> = runCatching {
        collectionRepository.updateCasinoGameOrder(
            identity = command.identity,
            gameIdentity = command.gameIdentity,
            order = command.order,
        )
    }
}
