package infrastructure.pam

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.util.concurrent.TimeUnit

/**
 * gRPC channel to pam-engine (user-engine), mirroring [infrastructure.wallet.walletChannel].
 * Keep-alive pings keep the HTTP/2 connection warm so a call after an idle gap doesn't pay a
 * reconnect.
 */
fun pamChannel(config: PamConfig): ManagedChannel =
    ManagedChannelBuilder
        .forAddress(config.address, config.port)
        .usePlaintext()
        .keepAliveTime(30, TimeUnit.SECONDS)
        .keepAliveTimeout(10, TimeUnit.SECONDS)
        .keepAliveWithoutCalls(true)
        .build()
