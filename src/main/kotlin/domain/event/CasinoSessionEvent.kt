package domain.event

import domain.model.CasinoSession
import kotlinx.serialization.KSerializer

data class CasinoSessionEvent(override val data: CasinoSession) : AppEvent<CasinoSession> {

    override val playerId = data.playerId.value

    companion object : AppEvent.Meta<CasinoSession> {
        override val route = "session.events"

        override val serializer: KSerializer<CasinoSession> = CasinoSessionWireSerializer

        override fun create(data: CasinoSession): AppEvent<CasinoSession> = CasinoSessionEvent(data)
    }
}
