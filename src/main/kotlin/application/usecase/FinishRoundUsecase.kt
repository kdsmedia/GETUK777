package application.usecase

import domain.exception.DomainException
import domain.model.Round
import domain.repository.IRoundRepository
import event.AppEventPublisher
import event.RoundEvent
import org.slf4j.LoggerFactory

class FinishRoundUsecase(
    private val roundRepository: IRoundRepository,
    private val eventPublisher: AppEventPublisher,
) {

    private val logger = LoggerFactory.getLogger(FinishRoundUsecase::class.java)

    suspend operator fun invoke(round: Round): Result<Unit> = runCatching {
        logger.info("Finishing round: id={} session={}", round.id, round.session.id)

        val finishedRound = roundRepository.save(round.finish())
        eventPublisher.publish(RoundEvent(finishedRound))

        logger.info("Round finished: id={}", finishedRound.id)
    }.onFailure { e ->
        if (e !is DomainException) {
            logger.error("Failed to finish round: id={} session={}", round.id, round.session.id, e)
        }
    }
}
