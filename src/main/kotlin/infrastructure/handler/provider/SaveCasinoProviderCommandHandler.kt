package infrastructure.handler.provider

import application.ICommandHandler
import application.command.provider.SaveCasinoProviderCommand
import domain.repository.IAggregatorRepository
import domain.repository.ICasinoProviderRepository
import domain.exception.domainRequireNotNull
import domain.exception.notfound.AggregatorNotFoundException
import domain.model.CasinoProvider

class SaveCasinoProviderCommandHandler(
    private val providerRepository: ICasinoProviderRepository,
    private val aggregatorRepository: IAggregatorRepository,
) : ICommandHandler<SaveCasinoProviderCommand, Unit> {

    override suspend fun handle(command: SaveCasinoProviderCommand): Result<Unit> = runCatching {
        val aggregator = domainRequireNotNull(
            aggregatorRepository.findByIdentity(command.aggregatorIdentity)
        ) { AggregatorNotFoundException() }

        val existing = providerRepository.findByIdentity(command.identity)
        val provider = existing?.copy(
            name = command.name,
            order = command.order,
            active = command.active,
            aggregator = aggregator,
            blockedCountry = command.blockedCountry,
            tags = command.tags,
        ) ?: CasinoProvider(
            identity = command.identity,
            name = command.name,
            order = command.order,
            active = command.active,
            aggregator = aggregator,
            blockedCountry = command.blockedCountry,
            tags = command.tags,
        )

        providerRepository.save(provider)
    }
}
