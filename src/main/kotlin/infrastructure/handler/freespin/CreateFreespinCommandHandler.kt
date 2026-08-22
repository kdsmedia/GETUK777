package infrastructure.handler.freespin

import application.ICommandHandler
import application.command.freespin.CreateFreespinCommand
import application.port.factory.IAggregatorFactory
import domain.exception.conflict.FreespinNotSupportedException
import domain.exception.domainRequire
import domain.exception.domainRequireNotNull
import domain.exception.notfound.AggregatorNotFoundException
import domain.exception.notfound.CasinoGameNotFoundException
import domain.model.Freespin
import domain.repository.IFreespinRepository
import domain.repository.IAggregatorRepository
import domain.repository.ICasinoGameVariantRepository
import domain.vo.Amount
import domain.vo.FreespinId
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class CreateFreespinCommandHandler(
    private val gameVariantRepository: ICasinoGameVariantRepository,
    private val freespinRepository: IFreespinRepository,
    private val aggregatorRepository: IAggregatorRepository,
    private val aggregatorFactory: IAggregatorFactory
) : ICommandHandler<CreateFreespinCommand, Unit> {

    override suspend fun handle(command: CreateFreespinCommand): Result<Unit> = runCatching {
        val variant = domainRequireNotNull(
            gameVariantRepository.findActiveByGameIdentity(command.gameIdentity)
        ) { CasinoGameNotFoundException() }

        domainRequire(variant.freeSpinEnable) { FreespinNotSupportedException() }

        val freespinAdapter = aggregatorFactory.createFreespinAdapter(
            domainRequireNotNull(aggregatorRepository.findByIntegration(variant.integration)) { AggregatorNotFoundException() }
        )

        freespinAdapter.create(
            presetValue = command.presetValues,
            referenceId = command.referenceId,
            playerId = command.playerId,
            gameSymbol = variant.symbol.value,
            currency = command.currency,
            startAt = command.startAt,
            endAt = command.endAt,
            spinAmount = command.spinAmount,
            spinCount = command.spinCount
        )

        // Recorded only after the provider accepted it. The other order would hand out a local
        // balance for a promotion the provider never registered, and every session opened against
        // it would be rejected; this way a failed provider call simply leaves nothing behind.
        freespinRepository.save(
            Freespin(
                referenceId = FreespinId(command.referenceId),
                playerId = command.playerId,
                gameVariant = variant,
                currency = command.currency,
                spinAmount = Amount(command.spinAmount),
                totalCount = command.spinCount,
                remainingCount = command.spinCount,
                startAt = command.startAt.toInstant(TimeZone.UTC),
                endAt = command.endAt.toInstant(TimeZone.UTC),
            )
        )
    }
}
