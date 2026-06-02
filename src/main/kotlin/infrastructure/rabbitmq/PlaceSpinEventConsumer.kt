package infrastructure.rabbitmq

import application.usecase.DecreasePlayerLimitUsecase
import com.rabbitmq.client.Channel
import domain.vo.Amount
import domain.vo.PlayerId
import event.SpinEvent
import event.AppEventConsumer
import event.model.SpinType

class PlaceSpinEventConsumer(
    channel: Channel,
    private val decreasePlayerLimit: DecreasePlayerLimitUsecase,
) : AppEventConsumer<SpinEvent>(channel, SpinEvent::class) {

    override suspend fun handle(event: SpinEvent) {
        val spin = event.data
        if (spin.type != SpinType.Place) return

        decreasePlayerLimit(PlayerId(spin.playerId), Amount(spin.amount))
    }
}
