package infrastructure.aggregator.tongame.adapter

import application.port.external.ICasinoGamePort
import domain.exception.conflict.DemoNotSupportedException
import domain.model.Freespin
import domain.model.Platform
import domain.model.CasinoSession
import domain.vo.Currency
import domain.vo.Locale
import infrastructure.aggregator.tongame.TongameConfig
import infrastructure.aggregator.tongame.client.TongameHttpClient
import io.ktor.http.URLBuilder

class TongameGameAdapter(
    private val config: TongameConfig,
) : ICasinoGamePort {

    private val client = TongameHttpClient(config)

    override suspend fun getAggregatorGames(): List<ICasinoGamePort.AggregatorGame> {
        return client.getGames().map { game ->
            ICasinoGamePort.AggregatorGame(
                symbol = game.identity,
                name = game.identity,
                providerName = PROVIDER_NAME,
                freeSpinEnable = false,
                freeChipEnable = false,
                jackpotEnable = false,
                demoEnable = false,
                bonusBuyEnable = false,
                locales = SUPPORTED_LOCALES,
                platforms = listOf(Platform.MOBILE)
            )
        }
    }

    /** TONGame has no demo mode — its game client requires a real, provider-minted session token. */
    override suspend fun getDemoUrl(
        gameSymbol: String,
        locale: Locale,
        platform: Platform,
        currency: Currency,
        lobbyUrl: String,
    ): String = throw DemoNotSupportedException()

    override suspend fun getLaunchUrl(session: CasinoSession, lobbyUrl: String, freespin: Freespin?): ICasinoGamePort.Launch {
        check(config.gameHost.isNotBlank()) { "TONGame game host not configured" }

        val gameSymbol = session.gameVariant.symbol.value

        // The token is ours: we register our own session.token with the provider (it mints nothing).
        // The provider then calls our `/player` webhook with this token to learn the player, and
        // echoes the token back as `sessionToken` in every wallet webhook, where we resolve the
        // exact session via findByToken.
        client.createSession(token = session.token.value)

        // The game client reads three query params — sessionToken, currency, operator — and replays
        // sessionToken + operator in its WS auth frame so the provider resolves our session.
        val url = URLBuilder("https://$gameSymbol.${config.gameHost}").apply {
            parameters.append("sessionToken", session.token.value)
            parameters.append("currency", session.currency.value)
            parameters.append("operator", config.operatorIdentity)
        }.buildString()

        return ICasinoGamePort.Launch(url)
    }

    companion object {
        private const val PROVIDER_NAME = "TONGame"

        private val SUPPORTED_LOCALES = listOf(Locale("en"))
    }
}
