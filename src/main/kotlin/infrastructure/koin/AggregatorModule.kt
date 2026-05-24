package infrastructure.koin

import infrastructure.aggregator.onegamehub.webhook.OneGameHubWebhook
import infrastructure.aggregator.pragmatic.webhook.PragmaticWebhook
import infrastructure.aggregator.tongame.webhook.TongameWalletGrpcService
import org.koin.dsl.module

val aggregatorModule = module {
    single { OneGameHubWebhook(bus = get()) }
    single { PragmaticWebhook(bus = get()) }
    single { TongameWalletGrpcService(bus = get()) }
}
