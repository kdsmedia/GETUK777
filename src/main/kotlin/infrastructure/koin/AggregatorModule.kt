package infrastructure.koin

import infrastructure.aggregator.gambutsoft.webhook.GambutsoftWebhook
import infrastructure.aggregator.gamingflow.webhook.GamingFlowWebhook
import infrastructure.aggregator.onegamehub.webhook.OneGameHubWebhook
import infrastructure.aggregator.pragmatic.webhook.PragmaticWebhook
import infrastructure.aggregator.skyline.webhook.SkylineWebhook
import infrastructure.aggregator.tech01sport.webhook.Tech01SportWebhook
import infrastructure.aggregator.tongame.webhook.TongameWebhook
import org.koin.dsl.module

val aggregatorModule = module {
    single { OneGameHubWebhook(bus = get(), currencyPort = get()) }
    single { PragmaticWebhook(bus = get(), currencyPort = get()) }
    single { TongameWebhook(bus = get(), playerPort = get(), walletPort = get()) }
    single { GamingFlowWebhook(bus = get(), currencyPort = get(), guardPort = get()) }
    single { Tech01SportWebhook(bus = get(), walletPort = get(), playerPort = get()) }
    single { GambutsoftWebhook(bus = get(), guardPort = get()) }
    single { SkylineWebhook(bus = get(), currencyPort = get(), guardPort = get()) }
}
