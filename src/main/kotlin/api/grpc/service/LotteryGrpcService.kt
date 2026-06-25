package api.grpc.service

import api.grpc.config.handleGrpcCall
import application.usecase.StreamLotteryUsecase
import com.nekgamebling.game.v1.LotteryClientFrame
import com.nekgamebling.game.v1.LotteryServerFrame
import com.nekgamebling.game.v1.LotteryServiceGrpcKt
import domain.vo.Currency
import domain.vo.PlayerId
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * WebSocket→gRPC bridge for the lottery. One bidirectional stream owns one upstream provider
 * socket for its lifetime. The first client frame is an init `{"playerId":...,"currency":...}`
 * consumed here to mint + authenticate the session; every later frame is forwarded verbatim,
 * and every provider frame is relayed verbatim. Whichever side ends first tears the bridge down.
 */
class LotteryGrpcService(
    private val streamLotteryUsecase: StreamLotteryUsecase,
) : LotteryServiceGrpcKt.LotteryServiceCoroutineImplBase() {

    override fun connect(requests: Flow<LotteryClientFrame>): Flow<LotteryServerFrame> = channelFlow {
        val clientFrames: ReceiveChannel<LotteryClientFrame> = requests.produceIn(this)

        val socket = handleGrpcCall {
            val (playerId, currency) = parseInit(clientFrames.receive().json)
            streamLotteryUsecase(playerId, currency).getOrThrow()
        }

        // provider -> client: relay snapshots/errors verbatim
        val upstream = launch {
            socket.incoming.collect { frame ->
                send(LotteryServerFrame.newBuilder().setJson(frame).build())
            }
        }

        // client -> provider: forward command frames (buy) verbatim
        val downstream = launch {
            for (frame in clientFrames) {
                socket.send(frame.json)
            }
        }

        try {
            select<Unit> {
                upstream.onJoin {}
                downstream.onJoin {}
            }
        } finally {
            upstream.cancel()
            downstream.cancel()
            socket.close()
        }
    }

    private fun parseInit(json: String): Pair<PlayerId, Currency> {
        val obj = Json.parseToJsonElement(json).jsonObject
        val playerId = PlayerId(obj["playerId"]?.jsonPrimitive?.contentOrNull.orEmpty())
        val currency = obj["currency"]?.jsonPrimitive?.contentOrNull?.let { Currency(it) } ?: DEFAULT_CURRENCY
        return playerId to currency
    }

    companion object {
        private val DEFAULT_CURRENCY = Currency("TON")
    }
}
