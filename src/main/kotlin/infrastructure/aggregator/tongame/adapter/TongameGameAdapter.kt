package infrastructure.aggregator.tongame.adapter

import application.port.external.IGamePort
import domain.model.Platform
import domain.model.Session
import domain.vo.Currency
import domain.vo.Locale
import infrastructure.aggregator.tongame.TongameConfig
import infrastructure.aggregator.tongame.client.TongameGrpcClient
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments

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
        check(config.gameDemoLaunchUrl.isNotBlank()) { "TONGame demo launch URL not configured" }

        return buildLaunchUrl(
            baseHost = config.gameDemoLaunchUrl,
            gameSymbol = gameSymbol,
            sessionToken = "",
        )
    }

    override suspend fun getLaunchUrl(session: Session, lobbyUrl: String): String {
        check(config.gameLaunchUrl.isNotBlank()) { "TONGame game launch URL not configured" }

        val sessionToken = client.createSession(
            playerId = session.playerId.value,
            gameId = session.gameVariant.symbol.value,
            currency = session.currency.value,
        )

        return buildLaunchUrl(
            baseHost = config.gameLaunchUrl,
            gameSymbol = session.gameVariant.symbol.value,
            sessionToken = sessionToken,
        )
    }

    private fun buildLaunchUrl(
        baseHost: String,
        gameSymbol: String,
        sessionToken: String,
    ): String {
        return URLBuilder("https://$baseHost").apply {
            appendPathSegments(gameSymbol)
            parameters.append("sessionToken", sessionToken)
            parameters.append("operatorIdentity", config.operatorIdentity)
        }.buildString()
    }

    companion object {
        private const val PROVIDER_NAME = "TONGame"

        private val SUPPORTED_LOCALES = listOf(Locale("en"))
    }
}
