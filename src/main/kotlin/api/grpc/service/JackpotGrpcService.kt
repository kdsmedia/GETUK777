package api.grpc.service

import application.port.external.JackpotState
import application.usecase.JackpotBroadcaster
import com.nekgamebling.game.v1.Empty
import com.nekgamebling.game.v1.JackpotDto
import com.nekgamebling.game.v1.JackpotPrizeDto
import com.nekgamebling.game.v1.JackpotServiceGrpcKt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Universal jackpot stream. Subscribers get the single shared upstream from
 * [JackpotBroadcaster] (latest frame replayed on connect, then live updates). The lottery's
 * one prize is the whole pool; the message is shaped as a generic prize list so the same
 * service can carry multi-prize jackpots later without a wire change.
 */
class JackpotGrpcService(
    private val broadcaster: JackpotBroadcaster,
) : JackpotServiceGrpcKt.JackpotServiceCoroutineImplBase() {

    override fun stream(request: Empty): Flow<JackpotDto> =
        broadcaster.stream().map { it.toProto() }

    private fun JackpotState.toProto(): JackpotDto =
        JackpotDto.newBuilder()
            .setIdentity(identity)
            .setCurrency(currency)
            .setStartAt(startAt)
            .setEndAt(endAt)
            .setStatus(status)
            .addPrizes(
                JackpotPrizeDto.newBuilder()
                    .setAmount(prizePool.toString())
                    .setIdentity(identity)
                    .build()
            )
            .build()
}
