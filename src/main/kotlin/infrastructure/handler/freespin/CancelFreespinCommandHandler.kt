package infrastructure.handler.freespin

import application.ICommandHandler
import application.command.freespin.CancelFreespinCommand
import application.port.factory.IAggregatorFactory
import domain.exception.domainRequireNotNull
import domain.exception.notfound.CasinoGameNotFoundException
import domain.repository.IFreespinRepository
import domain.repository.ICasinoGameVariantRepository
import domain.vo.FreespinId

class CancelFreespinCommandHandler(
    private val gameVariantRepository: ICasinoGameVariantRepository,
    private val freespinRepository: IFreespinRepository,
    private val aggregatorFactory: IAggregatorFactory
) : ICommandHandler<CancelFreespinCommand, Unit> {

    override suspend fun handle(command: CancelFreespinCommand): Result<Unit> = runCatching {
        val variant = domainRequireNotNull(
            gameVariantRepository.findActiveByGameIdentity(command.gameIdentity)
        ) { CasinoGameNotFoundException() }

        // Zeroed first: the remaining rounds are what make the grant spendable, and some providers
        // (GamingFlow) offer no revocation at all, so our own count is the only thing that stops it.
        freespinRepository.findByReferenceId(FreespinId(command.referenceId))
            ?.let { freespinRepository.save(it.cancel()) }

        val freespinAdapter = aggregatorFactory.createFreespinAdapter(variant.game.provider.aggregator)

        freespinAdapter.cancel(command.referenceId)
    }
}
