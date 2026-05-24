package infrastructure.aggregator.tongame.client.dto

import kotlinx.serialization.Serializable

@Serializable
data class ListGamesResponse(
    val games: List<GameDto>,
    val supportedCurrencies: List<String> = emptyList(),
)

@Serializable
data class GameDto(
    val identity: String,
    val name: String,
)

@Serializable
data class CreateSessionRequest(
    val sessionToken: String,
    val playerId: String,
    val gameId: String,
)
