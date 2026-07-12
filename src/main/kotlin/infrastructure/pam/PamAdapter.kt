package infrastructure.pam

import application.port.external.IPlayerPort
import com.nekgaming.userengine.proto.user.v1.AccountServiceGrpc
import com.nekgaming.userengine.proto.user.v1.FindUserRequest
import domain.vo.PlayerId
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PamAdapter(
    channel: ManagedChannel
) : IPlayerPort {

    private val stub: AccountServiceGrpc.AccountServiceBlockingStub =
        AccountServiceGrpc.newBlockingStub(channel)

    override suspend fun findPlayer(playerId: PlayerId): IPlayerPort.Player {
        val request = FindUserRequest.newBuilder()
            .setUserId(playerId.value.toLong())
            .build()

        val user = withContext(Dispatchers.IO) { stub.findUser(request) }.user

        // `User.avatar` (proto3 optional) carries the player's real avatar — the miniapp
        // writes the Telegram photo URL to PAM on provisioning. Pass it through as the
        // TONGame profilePic so games render the real picture instead of an initial.
        // A blank username (proto3 default for unset) falls back to the player id.
        val username = user.username
        val avatar = if (user.hasAvatar()) user.avatar.takeIf { it.isNotBlank() } else null
        return IPlayerPort.Player(
            username = if (username.isNullOrBlank()) playerId.value else username,
            profilePic = avatar,
        )
    }
}
