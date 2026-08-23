package infrastructure.aggregator.gambutsoft.adapter

import application.port.external.ICasinoGamePort
import domain.model.Freespin
import domain.model.Platform
import domain.model.CasinoSession
import domain.vo.Currency
import domain.vo.Locale
import infrastructure.aggregator.gambutsoft.GambutsoftConfig
import infrastructure.aggregator.gambutsoft.client.GambutsoftHttpClient
import infrastructure.aggregator.gambutsoft.client.dto.GameCatalogItemDto
import infrastructure.aggregator.gambutsoft.client.dto.OpenGameRequestDto

class GambutsoftGameAdapter(
    private val config: GambutsoftConfig,
) : ICasinoGamePort {

    private val client = GambutsoftHttpClient(config)

    /**
     * The vendor serves a different catalogue per currency, so the feed is the union of every
     * currency our account plays in. The union has to happen here rather than by running the sync
     * once per currency: provider matching measures how much of a provider's catalogue overlaps
     * with what we already store, and a currency-truncated feed drops that ratio far enough to mint
     * duplicate providers.
     */
    override suspend fun getAggregatorGames(): List<ICasinoGamePort.AggregatorGame> {
        val login = client.login()

        val userId = config.userId.ifBlank { login.user.id }
        check(userId.isNotBlank()) { "Gambutsoft user id not configured" }

        val currencies = config.catalogCurrencies
            .ifEmpty { login.user.currencies.map { it.currency.code } }
            .filter { it.isNotBlank() }
            .distinct()
        check(currencies.isNotEmpty()) { "Gambutsoft has no catalogue currency to sync" }

        return currencies
            .flatMap { client.getUserGames(accessToken = login.accessToken, userId = userId, currency = it) }
            .filter { it.isEnabled }
            .distinctBy { it.id }
            .map { it.toAggregatorGame() }
    }

    override suspend fun getDemoUrl(
        gameSymbol: String,
        locale: Locale,
        platform: Platform,
        currency: Currency,
        lobbyUrl: String,
    ): String {
        val content = client.openGame(
            OpenGameRequestDto(
                currency = currency.value,
                demo = DEMO_ON,
                exitUrl = exitUrl(lobbyUrl),
                gameId = gameSymbol,
                language = language(locale),
                playerLogin = config.demoLogin,
                userId = resolveUserId(),
            )
        )

        return content.game.url
    }

    /**
     * The provider mints the session id and echoes it as `sessionid` on every wallet callback, so
     * it is returned for persistence as `CasinoSession.externalToken` — it is the only key an
     * inbound callback can be resolved by. `player_login` carries our player id instead of the
     * session token so their player-level reporting stays truthful.
     *
     * [freespin] is ignored: the vendor exposes no freespin API, and free rounds attach at launch
     * through a `freespinTotalBet` whose unit the contract does not state. Nothing can grant a free
     * round on a Gambutsoft game either — the catalogue reports `freeSpinEnable = false`.
     */
    override suspend fun getLaunchUrl(session: CasinoSession, lobbyUrl: String, freespin: Freespin?): ICasinoGamePort.Launch {
        val content = client.openGame(
            OpenGameRequestDto(
                currency = session.currency.value,
                demo = DEMO_OFF,
                exitUrl = exitUrl(lobbyUrl),
                gameId = session.gameVariant.symbol.value,
                language = language(session.locale),
                playerLogin = session.playerId.value,
                userId = resolveUserId(),
                callbackUrl = config.callbackUrl.ifBlank { null },
            )
        )

        check(content.gameRes.sessionId.isNotBlank()) { "Gambutsoft openGame returned no session id" }

        return ICasinoGamePort.Launch(url = content.game.url, externalToken = content.gameRes.sessionId)
    }

    /** A launch never logs in. Every `/auth/login` invalidates the token issued before it, so
     *  falling back to one here would cancel the catalogue sync's token mid-run. */
    private fun resolveUserId(): String =
        config.userId.also { check(it.isNotBlank()) { "Gambutsoft user id not configured" } }

    /** `exitUrl` is required by the vendor while the launch command carries no lobby URL, so the
     *  configured value is what real play always falls back to. */
    private fun exitUrl(lobbyUrl: String): String = lobbyUrl.ifBlank { config.exitUrl }

    private fun language(locale: Locale): String = locale.value.lowercase().ifBlank { config.language }

    private fun GameCatalogItemDto.toAggregatorGame(): ICasinoGamePort.AggregatorGame =
        ICasinoGamePort.AggregatorGame(
            // The compound `<integration>:<provider>:<game>` id travels back verbatim as
            // `openGame.gameId`, so it is never split or normalized.
            symbol = id,
            name = title.ifBlank { id },
            providerName = provider.ifBlank { providerSlug(id) },
            // Nothing in the feed describes game features; the operator sets them.
            freeSpinEnable = false,
            freeChipEnable = false,
            jackpotEnable = false,
            demoEnable = true,
            bonusBuyEnable = false,
            // The feed states no languages, and an unsupported locale falls back to English anyway.
            locales = emptyList(),
            // Never empty: a session on a platform the variant does not list is refused outright.
            platforms = listOf(Platform.DESKTOP, Platform.MOBILE),
            tags = listOfNotNull(category.ifBlank { null }),
        )

    /** The middle segment of the game id is the vendor's stable provider slug — the fallback for a
     *  row whose display name is missing. */
    private fun providerSlug(id: String): String = id.split(ID_SEPARATOR).getOrNull(PROVIDER_SEGMENT) ?: id

    private companion object {
        const val DEMO_ON = "1"

        const val DEMO_OFF = "0"

        const val ID_SEPARATOR = ':'

        const val PROVIDER_SEGMENT = 1
    }
}
