package infrastructure.handler.session

import application.ICommandHandler
import application.command.session.RollbackSpinSessionCommand
import application.port.external.IWalletPort
import application.usecase.ProcessSpinUsecase
import domain.model.PlayerBalance
import domain.repository.ISpinRepository
import domain.service.SpinFactory
import domain.vo.ExternalSpinId

class RollbackSpinSessionHandler(
    private val spinRepository: ISpinRepository,
    private val processSpinUsecase: ProcessSpinUsecase,
    private val walletPort: IWalletPort,
) : ICommandHandler<RollbackSpinSessionCommand, PlayerBalance> {

    override suspend fun handle(command: RollbackSpinSessionCommand): Result<PlayerBalance> = runCatching {
        val session = command.session

        command.externalSpinIds.forEach { externalSpinId ->
            val original = spinRepository.findByExternalId(externalSpinId)

            // Nothing to reverse, or it belongs to a different session — skip rather than fail, so
            // one unknown leg never blocks the legs that do exist.
            if (original == null || original.round.session.id != session.id) return@forEach

            // The rollback id is derived from the spin it reverses, which makes it unique and makes
            // redelivery cheap to detect: providers retry a rollback until they get an answer.
            val rollbackId = "$externalSpinId$ROLLBACK_SUFFIX"
            if (spinRepository.findByExternalId(rollbackId) != null) return@forEach

            val spin = SpinFactory.rollback(
                round = original.round.copy(session = session),
                externalId = ExternalSpinId(rollbackId),
                reference = original,
            )

            processSpinUsecase.invoke(spin).getOrThrow()
        }

        walletPort.findBalance(playerId = session.playerId, currency = session.currency)
    }

    private companion object {
        const val ROLLBACK_SUFFIX = ":rollback"
    }
}
