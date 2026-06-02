package application.usecase

import application.port.external.IPlayerLimitPort
import domain.vo.Amount
import domain.vo.PlayerId
import org.slf4j.LoggerFactory

class DecreasePlayerLimitUsecase(
    private val playerLimitPort: IPlayerLimitPort,
) {

    private val logger = LoggerFactory.getLogger(DecreasePlayerLimitUsecase::class.java)

    suspend operator fun invoke(playerId: PlayerId, amount: Amount): Result<Unit> = runCatching {
        val currentLimit = playerLimitPort.getMaxPlaceAmount(playerId) ?: return@runCatching

        val newLimit = Amount(maxOf(0, currentLimit.value - amount.value))

        playerLimitPort.saveMaxPlaceAmount(playerId, newLimit)

        logger.info("Decreased player limit for [{}]: {} -> {}", playerId.value, currentLimit, newLimit)
    }
}
