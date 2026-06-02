package event

import event.model.Spin
import kotlinx.serialization.KSerializer

data class SpinEvent(override val data: Spin) : AppEvent<Spin> {

    override val playerId = data.playerId

    companion object : AppEvent.Meta<Spin> {
        override val route = "spin.events"

        override val serializer: KSerializer<Spin> = Spin.serializer()

        override fun create(data: Spin): AppEvent<Spin> = SpinEvent(data)
    }
}
