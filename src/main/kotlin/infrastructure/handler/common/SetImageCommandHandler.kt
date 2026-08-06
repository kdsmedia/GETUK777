package infrastructure.handler.common

import application.ICommandHandler
import application.command.collection.SetCollectionImageCommand
import application.command.common.SetImageCommand
import application.command.game.SetGameImageCommand
import application.command.provider.SetProviderImageCommand
import domain.exception.badrequest.BlankImageUrlException
import domain.exception.domainRequire
import domain.repository.ICollectionRepository
import domain.repository.IGameRepository
import domain.repository.IProviderRepository

/**
 * Single entry point for every `SetXImageCommand`.
 *
 * The command carries the final public URL — the engine never touches file
 * content or object storage. Dispatches to the correct repository's
 * `addImage(...)` based on the concrete command subtype.
 */
class SetImageCommandHandler(
    private val gameRepository: IGameRepository,
    private val providerRepository: IProviderRepository,
    private val collectionRepository: ICollectionRepository,
) : ICommandHandler<SetImageCommand, Unit> {

    override suspend fun handle(command: SetImageCommand): Result<Unit> = runCatching {
        domainRequire(command.url.isNotBlank()) { BlankImageUrlException() }

        when (command) {
            is SetGameImageCommand -> gameRepository.addImage(command.identity, command.key, command.url)
            is SetProviderImageCommand -> providerRepository.addImage(command.identity, command.key, command.url)
            is SetCollectionImageCommand -> collectionRepository.addImage(command.identity, command.key, command.url)
            else -> error("Unhandled SetImageCommand subtype: ${command::class.qualifiedName}")
        }
    }
}
