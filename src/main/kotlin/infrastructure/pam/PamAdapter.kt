package infrastructure.pam

import application.port.external.IPlayerPort
import com.nekgambling.pam.v1.FindUserRequest
import com.nekgambling.pam.v1.UserServiceGrpc
import domain.vo.PlayerId
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PamAdapter(
    channel: ManagedChannel
) : IPlayerPort {

    private val stub: UserServiceGrpc.UserServiceBlockingStub =
        UserServiceGrpc.newBlockingStub(channel)

    override suspend fun findPlayer(playerId: PlayerId): IPlayerPort.Player {
        val request = FindUserRequest.newBuilder()
            .setUserId(playerId.value.toLong())
            .build()

        // UserService.Find answers the User itself — there is no envelope to unwrap.
        val user = withContext(Dispatchers.IO) { stub.find(request) }

        // `User.avatar` carries the player's real avatar — the frontend writes the profile
        // picture to PAM on provisioning. Pass it through as the TONGame profilePic so games
        // render the real picture instead of an initial. It is a plain string, so "unset" and
        // "empty" are the same thing; a blank username falls back to the player id.
        val username = user.username
        val avatar = user.avatar.takeIf { it.isNotBlank() }
        return IPlayerPort.Player(
            username = if (username.isNullOrBlank()) playerId.value else username,
            profilePic = avatar,
        )
    }
}
