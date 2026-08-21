package infrastructure.handler.common

import application.ICommandHandler
import application.command.collection.SetCollectionImageCommand
import application.command.common.SetImageCommand
import application.command.game.SetCasinoGameImageCommand
import application.command.provider.SetCasinoProviderImageCommand
import domain.exception.badrequest.BlankImageUrlException
import domain.exception.domainRequire
import domain.repository.ICollectionRepository
import domain.repository.ICasinoGameRepository
import domain.repository.ICasinoProviderRepository

/**
 * Single entry point for every `SetXImageCommand`.
 *
 * The command carries the final public URL — the engine never touches file
 * content or object storage. Dispatches to the correct repository's
 * `addImage(...)` based on the concrete command subtype.
 */
class SetImageCommandHandler(
    private val gameRepository: ICasinoGameRepository,
    private val providerRepository: ICasinoProviderRepository,
    private val collectionRepository: ICollectionRepository,
) : ICommandHandler<SetImageCommand, Unit> {

    override suspend fun handle(command: SetImageCommand): Result<Unit> = runCatching {
        domainRequire(command.url.isNotBlank()) { BlankImageUrlException() }

        when (command) {
            is SetCasinoGameImageCommand -> gameRepository.addImage(command.identity, command.key, command.url)
            is SetCasinoProviderImageCommand -> providerRepository.addImage(command.identity, command.key, command.url)
            is SetCollectionImageCommand -> collectionRepository.addImage(command.identity, command.key, command.url)
            else -> error("Unhandled SetImageCommand subtype: ${command::class.qualifiedName}")
        }
    }
}
