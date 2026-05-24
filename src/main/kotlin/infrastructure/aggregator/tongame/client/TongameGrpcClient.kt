package infrastructure.aggregator.tongame.client

import infrastructure.aggregator.tongame.TongameConfig
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import slot.v1.dto.CreateSessionRequest
import slot.v1.dto.Game
import slot.v1.dto.ListGamesRequest
import slot.v1.service.GameServiceGrpcKt
import slot.v1.service.SessionServiceGrpcKt

/**
 * Outbound client for the TONGame provider gRPC server. Authenticates every call with the
 * operator's `x-identity` + `x-api-key` metadata (slot.v1 §3). The channel is lazy so the
 * launch/demo URL paths — which never dial the provider — don't open a connection.
 */
class TongameGrpcClient(
    private val config: TongameConfig,
) {

    private val channel by lazy {
        ManagedChannelBuilder
            .forTarget(config.address)
            .usePlaintext()
            .build()
    }

    private val gameStub by lazy {
        GameServiceGrpcKt.GameServiceCoroutineStub(channel)
    }

    private val sessionStub by lazy {
        SessionServiceGrpcKt.SessionServiceCoroutineStub(channel)
    }

    private val authMetadata: Metadata
        get() = Metadata().apply {
            put(IDENTITY_KEY, config.operatorIdentity)
            put(API_KEY_KEY, config.apiKey)
        }

    suspend fun listGames(): List<Game> =
        gameStub.list(ListGamesRequest.getDefaultInstance(), authMetadata).gamesList

    /** Opens a session on the provider and returns the minted session token (slot.v1 §5). */
    suspend fun createSession(playerId: String, gameId: String, currency: String): String =
        sessionStub.createSession(
            CreateSessionRequest.newBuilder()
                .setIdentity(config.operatorIdentity)
                .setPlayerId(playerId)
                .setGameId(gameId)
                .setCurrency(currency)
                .build(),
            authMetadata,
        ).sessionToken

    companion object {
        private val IDENTITY_KEY: Metadata.Key<String> =
            Metadata.Key.of("x-identity", Metadata.ASCII_STRING_MARSHALLER)

        private val API_KEY_KEY: Metadata.Key<String> =
            Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER)
    }
}
