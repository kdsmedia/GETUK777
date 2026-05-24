package infrastructure.aggregator.tongame.client.dto

import kotlinx.serialization.Serializable

@Serializable
data class ListGamesResponse(
    val games: List<GameDto>,
)

@Serializable
data class GameDto(
    val identity: String,
    val currency: String,
    val name: String,
)

@Serializable
data class CreateSessionRequest(
    val identity: String,
    val playerId: String,
    val gameId: String,
    val currency: String,
)

@Serializable
data class CreateSessionResponse(
    val sessionToken: String,
    val expiresAt: Long,
)
