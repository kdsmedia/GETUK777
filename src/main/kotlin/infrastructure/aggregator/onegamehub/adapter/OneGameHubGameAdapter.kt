package infrastructure.aggregator.onegamehub.adapter

import application.port.external.IGamePort
import domain.model.Platform
import domain.model.Session
import domain.vo.Currency
import domain.vo.Locale
import infrastructure.aggregator.onegamehub.OneGameHubConfig
import infrastructure.aggregator.onegamehub.client.OneGameHubHttpClient

class OneGameHubGameAdapter(
    config: OneGameHubConfig,
) : IGamePort {

    private val client = OneGameHubHttpClient(config)

    override suspend fun getAggregatorGames(): List<IGamePort.AggregatorGame> {
        val response = client.listGames()

        check(response.success) { "OneGameHub listGames failed with status ${response.status}" }

        val games = response.response ?: emptyList()

        return games.map { game ->
            val tags = (game.categories + game.subcategories)
                .flatMap { it.split(TAG_SEPARATOR) }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            IGamePort.AggregatorGame(
                symbol = game.id,
                name = game.name,
                providerName = game.provider.ifBlank { game.brand },
                freeSpinEnable = game.freespinEnable,
                freeChipEnable = false,
                jackpotEnable = false,
                demoEnable = game.demoEnable,
                bonusBuyEnable = tags.any { it.startsWith(BUY_BONUS_TAG_PREFIX) },
                locales = emptyList(),
                platforms = listOf(Platform.DESKTOP, Platform.MOBILE),
                playLines = game.paylines,
                tags = tags,
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
        val response = client.getLaunchUrl(
            gameSymbol = gameSymbol,
            sessionToken = "",
            playerId = "",
            locale = locale.value,
            platform = platform,
            currency = currency.value,
            lobbyUrl = lobbyUrl,
            demo = true
        )

        check(response.success) { "OneGameHub getDemoUrl failed with status ${response.status}" }

        return response.response?.gameUrl
            ?: error("No game URL returned from OneGameHub for demo")
    }

    override suspend fun getLaunchUrl(session: Session, lobbyUrl: String): String {
        val response = client.getLaunchUrl(
            gameSymbol = session.gameVariant.symbol.value,
            sessionToken = session.token.value,
            playerId = session.playerId.value,
            locale = session.locale.value,
            platform = session.platform,
            currency = session.currency.value,
            lobbyUrl = lobbyUrl,
            demo = false
        )

        check(response.success) { "OneGameHub getLaunchUrl failed with status ${response.status}" }

        return response.response?.gameUrl
            ?: error("No game URL returned from OneGameHub")
    }

    private companion object {
        const val TAG_SEPARATOR = ","

        const val BUY_BONUS_TAG_PREFIX = "buy-bonus"

        const val ICON_KEY = "icon"

        const val THUMBNAIL_KEY = "thumbnail"

        const val PREFERRED_THUMBNAIL_SIZE = "500x500"
    }
}
