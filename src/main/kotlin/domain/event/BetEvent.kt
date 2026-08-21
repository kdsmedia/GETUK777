package domain.event

import domain.model.Bet
import kotlinx.serialization.KSerializer

data class BetEvent(override val data: Bet) : AppEvent<Bet> {

    override val playerId = data.playerId.value

    companion object : AppEvent.Meta<Bet> {
        override val route = "bet.events"

        override val serializer: KSerializer<Bet> = Bet.serializer()

        override fun create(data: Bet): AppEvent<Bet> = BetEvent(data)
    }
}
