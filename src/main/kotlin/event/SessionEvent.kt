package event

import event.model.Session
import kotlinx.serialization.KSerializer

data class SessionEvent(override val data: Session) : AppEvent<Session> {

    override val playerId = data.playerId

    companion object : AppEvent.Meta<Session> {
        override val route = "session.events"

        override val serializer: KSerializer<Session> = Session.serializer()

        override fun create(data: Session): AppEvent<Session> = SessionEvent(data)
    }
}
