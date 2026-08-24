package infrastructure.aggregator.skyline.adapter

import application.port.external.ICasinoGamePort
import domain.model.Freespin
import domain.model.Platform
import domain.model.CasinoSession
import domain.vo.Currency
import domain.vo.Locale
import infrastructure.aggregator.skyline.SkylineAction
import infrastructure.aggregator.skyline.SkylineConfig
import infrastructure.aggregator.skyline.client.SkylineHttpClient
import infrastructure.aggregator.skyline.client.dto.SkylineGameDto
import infrastructure.aggregator.skyline.client.dto.SkylineLaunchDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

class SkylineGameAdapter(
    private val config: SkylineConfig,
) : ICasinoGamePort {

    private val client = SkylineHttpClient(config)

    /**
     * Skyline is one provider serving one catalogue, so every game is filed under the single
     * configured provider name. The feed states no platforms, locales or features — only an id, a
     * title and artwork — so the rest is defaulted and left to the operator.
     */
    override suspend fun getAggregatorGames(): List<ICasinoGamePort.AggregatorGame> =
        client.call(SkylineAction.GAME_LIST, emptyMap(), ListSerializer(SkylineGameDto.serializer()))
            .distinctBy { it.gameId }
            .map { game ->
                ICasinoGamePort.AggregatorGame(
                    symbol = game.gameId,
                    name = game.gameTitle.ifBlank { game.gameId },
                    providerName = config.providerName,
                    freeSpinEnable = true,
                    freeChipEnable = false,
                    jackpotEnable = false,
                    demoEnable = true,
                    bonusBuyEnable = false,
                    // Never empty: a session on a platform the variant does not list is refused.
                    platforms = listOf(Platform.DESKTOP, Platform.MOBILE),
                    locales = emptyList(),
                    tags = listOfNotNull(game.category?.ifBlank { null }),
                )
            }

    /** Demo moves no money and the vendor records nothing for it — no wallet callback ever follows. */
    override suspend fun getDemoUrl(
        gameSymbol: String,
        locale: Locale,
        platform: Platform,
        currency: Currency,
        lobbyUrl: String,
    ): String = launch(
        gameSymbol = gameSymbol,
        sessionToken = DEMO_SESSION,
        playerId = DEMO_PLAYER,
        currency = currency,
        locale = locale,
        platform = platform,
        lobbyUrl = lobbyUrl,
        demo = true,
    )

    /**
     * WE mint the session token and they echo it back on every wallet callback, so nothing is
     * persisted as `externalToken` — the session already resolves by the token we handed out.
     *
     * [freespin] is not passed: free rounds are granted ahead of time through `bonus_award` and
     * arrive on the wire as the `bonus` field of a callback, not as a launch parameter.
     */
    override suspend fun getLaunchUrl(session: CasinoSession, lobbyUrl: String, freespin: Freespin?): ICasinoGamePort.Launch =
        ICasinoGamePort.Launch(
            url = launch(
                gameSymbol = session.gameVariant.symbol.value,
                sessionToken = session.token.value,
                playerId = session.playerId.value,
                currency = session.currency,
                locale = session.locale,
                platform = session.platform,
                lobbyUrl = lobbyUrl,
                demo = false,
            )
        )

    private suspend fun launch(
        gameSymbol: String,
        sessionToken: String,
        playerId: String,
        currency: Currency,
        locale: Locale,
        platform: Platform,
        lobbyUrl: String,
        demo: Boolean,
    ): String {
        val params = buildMap<String, JsonElement> {
            put(FIELD_SESSION, JsonPrimitive(sessionToken))
            put(FIELD_CASINO, JsonPrimitive(config.casino))
            put(FIELD_GAME, JsonPrimitive(gameSymbol))
            put(FIELD_CURRENCY, JsonPrimitive(currency.value))
            put(FIELD_PLAYER_ID, JsonPrimitive(playerId))
            put(FIELD_LANGUAGE, JsonPrimitive(language(locale)))
            put(FIELD_PLATFORM, JsonPrimitive(platform(platform)))
            put(FIELD_LOBBY, JsonPrimitive(lobbyUrl))
            put(FIELD_CASHIER, JsonPrimitive(config.cashierUrl))
            // Mandatory at the vendor and unknown to the casino context; see SkylineConfig.
            put(FIELD_PLAYER_IP, JsonPrimitive(config.defaultPlayerIp))
            put(FIELD_COUNTRY, JsonPrimitive(config.defaultCountry))
            if (demo) put(FIELD_DEMO, JsonPrimitive(true))
        }

        val launch = client.call(SkylineAction.GAME_LAUNCH, params, SkylineLaunchDto.serializer())
        check(launch.launchUrl.isNotBlank()) { "Skyline game_launch returned no launch url" }

        return launch.launchUrl
    }

    private fun language(locale: Locale): String = locale.value.lowercase().ifBlank { config.language }

    /** They know two platforms; a download client is served the desktop build. */
    private fun platform(platform: Platform): String =
        if (platform == Platform.MOBILE) PLATFORM_MOBILE else PLATFORM_DESKTOP

    private companion object {
        const val FIELD_SESSION = "session"

        const val FIELD_CASINO = "casino"

        const val FIELD_GAME = "game"

        const val FIELD_CURRENCY = "currency"

        const val FIELD_PLAYER_ID = "player_id"

        const val FIELD_LANGUAGE = "language"

        const val FIELD_PLATFORM = "platform"

        const val FIELD_LOBBY = "lobby"

        /** Spelled `cassier` in their contract; the typo is theirs and has to be reproduced. */
        const val FIELD_CASHIER = "cassier"

        const val FIELD_PLAYER_IP = "player_ip"

        const val FIELD_COUNTRY = "country"

        const val FIELD_DEMO = "demo"

        const val PLATFORM_MOBILE = "mobile"

        const val PLATFORM_DESKTOP = "desktop"

        /** A demo launch still needs a session and a player; neither is ever looked up again,
         *  because the vendor makes no callback for demo play. */
        const val DEMO_SESSION = "demo"

        const val DEMO_PLAYER = "demo"
    }
}
