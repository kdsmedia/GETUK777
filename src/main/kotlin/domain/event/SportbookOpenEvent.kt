package domain.event

import domain.model.SportbookSession
import kotlinx.serialization.KSerializer

data class SportbookOpenEvent(override val data: SportbookSession) : AppEvent<SportbookSession> {

    override val playerId = data.playerId.value

    companion object : AppEvent.Meta<SportbookSession> {
        override val route = "sportbook.events"

        override val serializer: KSerializer<SportbookSession> = SportbookSession.serializer()

        override fun create(data: SportbookSession): AppEvent<SportbookSession> = SportbookOpenEvent(data)
    }
}
