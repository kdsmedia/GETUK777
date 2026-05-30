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

        // The published user-grpc-client jar exposes User.username, but its Profile
        // predates the avatar field, so an avatar can't be read here — return null
        // (profilePic is optional in the TONGame contract). A blank username (proto3
        // default for unset) falls back to the player id.
        val username = user.username
        return IPlayerPort.Player(
            username = if (username.isNullOrBlank()) playerId.value else username,
            profilePic = null,
        )
    }
}
