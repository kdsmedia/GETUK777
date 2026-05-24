package infrastructure.aggregator.tongame.adapter

import application.port.external.IGamePort
import domain.model.Platform
import domain.model.Session
import domain.vo.Currency
import domain.vo.Locale
import infrastructure.aggregator.tongame.TongameConfig
import infrastructure.aggregator.tongame.client.TongameHttpClient
import io.ktor.http.URLBuilder

class TongameGameAdapter(
    private val config: TongameConfig,
) : IGamePort {

    private val client = TongameHttpClient(config)

    override suspend fun getAggregatorGames(): List<IGamePort.AggregatorGame> {
        return client.getGames().map { game ->
            IGamePort.AggregatorGame(
                symbol = game.identity,
                name = game.name,
                providerName = PROVIDER_NAME,
                freeSpinEnable = false,
                freeChipEnable = false,
                jackpotEnable = false,
                demoEnable = false,
                bonusBuyEnable = false,
                locales = SUPPORTED_LOCALES,
                platforms = listOf(Platform.DESKTOP, Platform.MOBILE)
            )
        }
    }

    override suspend fun getDemoUrl(
        gameSymbol: String,
        locale: Locale,
        platform: Platform,
        currency: Currency,
        lobbyUrl: String,
    ): String {
        return buildGameUrl(gameSymbol) {
            parameters.append("mode", "demo")
        }
    }

    override suspend fun getLaunchUrl(session: Session, lobbyUrl: String): String {
        val gameSymbol = session.gameVariant.symbol.value

        // We mint the token; the provider stores it and echoes it back in wallet webhooks,
        // so it resolves via our own findByToken.
        client.createSession(
            sessionToken = session.token.value,
            playerId = session.playerId.value,
            gameId = gameSymbol,
        )

        return buildGameUrl(gameSymbol) {
            parameters.append("mode", "real")
            parameters.append("sessionToken", session.token.value)
            parameters.append("operatorIdentity", config.operatorIdentity)
        }
    }

    private fun buildGameUrl(gameSymbol: String, query: URLBuilder.() -> Unit): String {
        check(config.gameHost.isNotBlank()) { "TONGame game host not configured" }

        return URLBuilder("https://$gameSymbol.${config.gameHost}").apply(query).buildString()
    }

    companion object {
        private const val PROVIDER_NAME = "TONGame"

        private val SUPPORTED_LOCALES = listOf(Locale("en"))
    }
}
