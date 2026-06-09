package infrastructure.koin

import application.port.external.FileAdapter
import application.port.external.IBackgroundTaskPort
import application.port.external.ICurrencyPort
import application.port.external.IPlayerLimitPort
import application.port.external.IPlayerPort
import application.port.external.IWalletPort
import application.port.factory.AggregatorAdapterProvider
import application.port.factory.IAggregatorFactory
import com.rabbitmq.client.Channel
import domain.event.AppEventPublisher
import infrastructure.aggregator.AggregatorRegistry
import infrastructure.aggregator.onegamehub.OneGameHubAdapterProvider
import infrastructure.aggregator.pateplay.PateplayAdapterProvider
import infrastructure.aggregator.pragmatic.PragmaticAdapterProvider
import infrastructure.aggregator.tongame.TongameAdapterProvider
import infrastructure.pam.PamAdapter
import infrastructure.pam.pamChannel
import infrastructure.rabbitmq.PlaceSpinEventConsumer
import infrastructure.rabbitmq.RabbitAppEventPublisher
import infrastructure.rabbitmq.rabbitMqChannel
import infrastructure.redis.PlayerLimitRedis
import infrastructure.s3.S3FileAdapter
import infrastructure.util.BackgroundWorker
import infrastructure.wallet.CurrencyAdapter
import infrastructure.wallet.WalletAdapter
import infrastructure.wallet.walletChannel
import io.grpc.ManagedChannel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val externalModule = module {
    // One shared gRPC channel to wallet-engine for both wallet + currency adapters.
    single<ManagedChannel> { walletChannel(get()) }
    single<IWalletPort> { WalletAdapter(channel = get()) }
    single<FileAdapter> { S3FileAdapter(config = get()) }
    single<IPlayerLimitPort> { PlayerLimitRedis(config = get()) }
    single<ICurrencyPort> { CurrencyAdapter(channel = get()) }
    // Separate gRPC channel to pam-engine (user-engine) for player profile lookups.
    single<IPlayerPort> { PamAdapter(channel = pamChannel(get())) }
    single<IBackgroundTaskPort> { BackgroundWorker() }
    // One shared RabbitMQ channel backs the central publisher + every consumer.
    single<Channel> { rabbitMqChannel(get()) }
    single<AppEventPublisher> { RabbitAppEventPublisher(channel = get()) }

    // Aggregator providers — add a new aggregator by binding another AggregatorAdapterProvider.
    single(named("onegamehub")) { OneGameHubAdapterProvider() } bind AggregatorAdapterProvider::class
    single(named("pragmatic")) { PragmaticAdapterProvider() } bind AggregatorAdapterProvider::class
    single(named("pateplay")) { PateplayAdapterProvider() } bind AggregatorAdapterProvider::class
    single(named("tongame")) { TongameAdapterProvider() } bind AggregatorAdapterProvider::class
    single<IAggregatorFactory> {
        AggregatorRegistry(providers = getAll<AggregatorAdapterProvider>())
    }

    single { PlaceSpinEventConsumer(channel = get(), decreasePlayerLimit = get()) }
}
