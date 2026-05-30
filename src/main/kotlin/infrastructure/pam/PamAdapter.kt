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

        return IPlayerPort.Player(
            username = if (user.hasUsername()) user.username else playerId.value,
            profilePic = if (user.hasProfile() && user.profile.hasAvatar()) user.profile.avatar else null,
        )
    }
}
