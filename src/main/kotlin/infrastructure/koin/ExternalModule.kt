package infrastructure.koin

import application.port.external.FileAdapter
import application.port.external.IBackgroundTaskPort
import application.port.external.ICurrencyPort
import application.port.external.IEventPort
import application.port.external.IPlayerLimitPort
import application.port.external.IWalletPort
import application.port.factory.AggregatorAdapterProvider
import application.port.factory.IAggregatorFactory
import infrastructure.aggregator.AggregatorRegistry
import infrastructure.aggregator.onegamehub.OneGameHubAdapterProvider
import infrastructure.aggregator.pateplay.PateplayAdapterProvider
import infrastructure.aggregator.pragmatic.PragmaticAdapterProvider
import infrastructure.aggregator.tongame.TongameAdapterProvider
import infrastructure.rabbitmq.PlaceSpinEventConsumer
import infrastructure.rabbitmq.RabbitMqEventPublisher
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
    single<IBackgroundTaskPort> { BackgroundWorker() }
    single<IEventPort> { RabbitMqEventPublisher(application = get()) }

    // Aggregator providers — add a new aggregator by binding another AggregatorAdapterProvider.
    single(named("onegamehub")) { OneGameHubAdapterProvider() } bind AggregatorAdapterProvider::class
    single(named("pragmatic")) { PragmaticAdapterProvider() } bind AggregatorAdapterProvider::class
    single(named("pateplay")) { PateplayAdapterProvider() } bind AggregatorAdapterProvider::class
    single(named("tongame")) { TongameAdapterProvider() } bind AggregatorAdapterProvider::class
    single<IAggregatorFactory> {
        AggregatorRegistry(providers = getAll<AggregatorAdapterProvider>())
    }

    single { PlaceSpinEventConsumer(application = get(), playerLimitPort = get()) }
}
