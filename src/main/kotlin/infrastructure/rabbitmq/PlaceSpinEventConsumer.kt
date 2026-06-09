package infrastructure.rabbitmq

import application.usecase.DecreasePlayerLimitUsecase
import com.rabbitmq.client.Channel
import domain.model.SpinType
import event.AppEventConsumer
import event.SpinEvent

class PlaceSpinEventConsumer(
    channel: Channel,
    private val decreasePlayerLimit: DecreasePlayerLimitUsecase,
) : AppEventConsumer<SpinEvent>(channel, SpinEvent::class) {

    override suspend fun handle(event: SpinEvent) {
        val spin = event.data
        if (spin.type != SpinType.PLACE) return

        decreasePlayerLimit(spin.round.session.playerId, spin.amount)
    }
}
