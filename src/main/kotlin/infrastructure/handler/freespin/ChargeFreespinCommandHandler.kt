package infrastructure.handler.freespin

import application.ICommandHandler
import application.command.freespin.ChargeFreespinCommand
import domain.exception.domainRequireNotNull
import domain.exception.notfound.FreespinNotFoundException
import domain.model.Freespin
import domain.repository.IFreespinRepository
import domain.vo.FreespinId

class ChargeFreespinCommandHandler(
    private val freespinRepository: IFreespinRepository,
) : ICommandHandler<ChargeFreespinCommand, Freespin> {

    override suspend fun handle(command: ChargeFreespinCommand): Result<Freespin> = runCatching {
        val freespin = domainRequireNotNull(
            freespinRepository.findByReferenceId(FreespinId(command.referenceId))
        ) { FreespinNotFoundException() }

        freespinRepository.save(freespin.charge(command.count))
    }
}
