package infrastructure.aggregator.tongame.client.dto

import kotlinx.serialization.Serializable

@Serializable
data class GameDto(
    val identity: String,
)

@Serializable
data class CreateSessionRequest(
    val playerId: String,
)

@Serializable
data class CreateSessionResponse(
    val token: String,
    val expireAt: String? = null,
)
