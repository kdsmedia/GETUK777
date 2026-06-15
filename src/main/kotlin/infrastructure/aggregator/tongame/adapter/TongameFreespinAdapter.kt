package infrastructure.aggregator.tongame.adapter

import application.port.external.IFreespinPort
import domain.exception.conflict.FreespinNotSupportedException
import domain.vo.Currency
import domain.vo.PlayerId
import kotlinx.datetime.LocalDateTime

/** slot.v1 has no freespin support — every operation is rejected. */
class TongameFreespinAdapter : IFreespinPort {

    override suspend fun getPreset(gameSymbol: String): Map<String, Any> =
        throw FreespinNotSupportedException()

    override suspend fun create(
        presetValue: Map<String, Any>,
        referenceId: String,
        playerId: PlayerId,
        gameSymbol: String,
        currency: Currency,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
        spinAmount: Long,
        spinCount: Int
    ): Unit = throw FreespinNotSupportedException()

    override suspend fun cancel(referenceId: String): Unit = throw FreespinNotSupportedException()
}
