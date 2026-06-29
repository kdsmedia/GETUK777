package api.grpc.service

import application.port.external.JackpotState
import application.usecase.JackpotBroadcaster
import com.nekgamebling.game.v1.JackpotDto
import com.nekgamebling.game.v1.JackpotPrizeDto
import com.nekgamebling.game.v1.JackpotServiceGrpcKt
import com.nekgamebling.game.v1.JackpotStreamRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Universal jackpot stream, resolved PER AGGREGATOR (provider): the request names the aggregator,
 * and subscribers get that provider's shared upstream from [JackpotBroadcaster] (latest frame
 * replayed on connect, then live updates). A jackpot's prizes are a generic list, so the same
 * service carries single- or multi-prize jackpots from any provider without a wire change.
 */
class JackpotGrpcService(
    private val broadcaster: JackpotBroadcaster,
) : JackpotServiceGrpcKt.JackpotServiceCoroutineImplBase() {

    override fun stream(request: JackpotStreamRequest): Flow<JackpotDto> =
        broadcaster.stream(request.provider, request.identity).map { it.toProto() }

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
