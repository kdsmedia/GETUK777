package application.port.external

import domain.vo.PlayerId

/**
 * Port for fetching a player's display profile from the platform's PAM service.
 * Implementations connect to pam-engine over gRPC.
 */
interface IPlayerPort {
    /**
     * Resolve a player's display info (username + optional avatar) by id.
     */
    suspend fun findPlayer(playerId: PlayerId): Player

    data class Player(
        val username: String,
        val profilePic: String?,
        val country: String? = null,
    )
}
