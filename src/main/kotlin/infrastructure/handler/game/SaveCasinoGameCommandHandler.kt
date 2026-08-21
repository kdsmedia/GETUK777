package infrastructure.handler.game

import application.ICommandHandler
import application.command.game.SaveCasinoGameCommand
import domain.repository.ICasinoGameRepository
import domain.repository.ICasinoProviderRepository
import domain.exception.domainRequireNotNull
import domain.exception.notfound.CasinoProviderNotFoundException
import domain.model.CasinoGame

class SaveCasinoGameCommandHandler(
    private val gameRepository: ICasinoGameRepository,
    private val providerRepository: ICasinoProviderRepository,
) : ICommandHandler<SaveCasinoGameCommand, Unit> {

    override suspend fun handle(command: SaveCasinoGameCommand): Result<Unit> = runCatching {
        val provider = domainRequireNotNull(
            providerRepository.findByIdentity(command.providerIdentity)
        ) { CasinoProviderNotFoundException() }

        val existing = gameRepository.findByIdentity(command.identity)
        val game = existing?.copy(
            name = command.name,
            provider = provider,
            bonusBetEnable = command.bonusBetEnable,
            bonusWageringEnable = command.bonusWageringEnable,
            tags = command.tags,
            active = command.active,
            order = command.order,
        ) ?: CasinoGame(
            identity = command.identity,
            name = command.name,
            provider = provider,
            bonusBetEnable = command.bonusBetEnable,
            bonusWageringEnable = command.bonusWageringEnable,
            tags = command.tags,
            active = command.active,
            order = command.order,
        )

        gameRepository.save(game)
    }
}
