package domain.event

import domain.model.Round
import kotlinx.serialization.KSerializer

data class RoundEvent(override val data: Round) : AppEvent<Round> {

    override val playerId = data.session.playerId.value

    companion object : AppEvent.Meta<Round> {
        override val route = "round.events"

        override val serializer: KSerializer<Round> = Round.serializer()

        override fun create(data: Round): AppEvent<Round> = RoundEvent(data)
    }
}
