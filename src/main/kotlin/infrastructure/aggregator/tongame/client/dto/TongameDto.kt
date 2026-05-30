package infrastructure.aggregator.tongame.client.dto

import kotlinx.serialization.Serializable

@Serializable
data class GameDto(
    val identity: String,
)

@Serializable
data class CreateSessionRequest(
    val token: String,
)
