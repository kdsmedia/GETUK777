package domain.event

import domain.model.Session
import kotlinx.serialization.KSerializer

data class SessionEvent(override val data: Session) : AppEvent<Session> {

    override val playerId = data.playerId.value

    companion object : AppEvent.Meta<Session> {
        override val route = "session.events"

        override val serializer: KSerializer<Session> = SessionWireSerializer

        override fun create(data: Session): AppEvent<Session> = SessionEvent(data)
    }
}
