package infrastructure.aggregator.tongame.adapter

import application.port.external.IGamePort
import domain.model.Platform
import domain.model.Session
import domain.vo.Currency
import domain.vo.Locale
import infrastructure.aggregator.tongame.TongameConfig
import infrastructure.aggregator.tongame.client.TongameGrpcClient
import io.ktor.http.URLBuilder

class TongameGameAdapter(
    private val config: TongameConfig,
) : IGamePort {

    private val client = TongameGrpcClient(config)

    override suspend fun getAggregatorGames(): List<IGamePort.AggregatorGame> {
        return client.listGames().map { game ->
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

        val sessionToken = client.createSession(
            playerId = session.playerId.value,
            gameId = gameSymbol,
            currency = session.currency.value,
        )

        return buildGameUrl(gameSymbol) {
            parameters.append("mode", "real")
            parameters.append("sessionToken", sessionToken)
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
