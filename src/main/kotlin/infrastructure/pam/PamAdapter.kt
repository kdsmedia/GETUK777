package infrastructure.pam

import application.port.external.IPlayerPort
import com.nekgaming.userengine.proto.user.v1.AccountServiceGrpc
import com.nekgaming.userengine.proto.user.v1.FindUserRequest
import domain.vo.PlayerId
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Player profile lookups backed by pam-engine's gRPC AccountService. PAM keys users by an
 * internal numeric id, which is exactly the value carried by [PlayerId].
 */
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

        // Read via plain getters (not optional-presence accessors): the published
        // user-grpc-client proto exposes username/profile.avatar but not has* on them.
        // Unset proto3 strings default to "", which we treat as absent.
        return IPlayerPort.Player(
            username = user.username.ifBlank { playerId.value },
            profilePic = user.profile.avatar.ifBlank { null },
        )
    }
}
