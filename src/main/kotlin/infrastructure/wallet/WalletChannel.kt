package infrastructure.wallet

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.util.concurrent.TimeUnit

/**
 * Single shared gRPC channel to wallet-engine, reused by [WalletAdapter] and
 * [CurrencyAdapter] instead of each opening its own. Keep-alive pings keep the
 * HTTP/2 connection warm so a call after an idle gap doesn't pay a reconnect.
 */
fun walletChannel(config: WalletConfig): ManagedChannel =
    ManagedChannelBuilder
        .forAddress(config.address, config.port)
        .usePlaintext()
        .keepAliveTime(30, TimeUnit.SECONDS)
        .keepAliveTimeout(10, TimeUnit.SECONDS)
        .keepAliveWithoutCalls(true)
        .build()
