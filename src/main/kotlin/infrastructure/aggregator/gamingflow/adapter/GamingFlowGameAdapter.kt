package infrastructure.aggregator.gamingflow.adapter

import application.port.external.IGamePort
import domain.model.Platform
import domain.model.Session
import domain.vo.Currency
import domain.vo.Locale
import infrastructure.aggregator.gamingflow.GamingFlowConfig
import infrastructure.aggregator.gamingflow.client.GamingFlowHttpClient
import infrastructure.aggregator.gamingflow.client.dto.GameDto
import infrastructure.aggregator.gamingflow.client.dto.SessionResultDto

class GamingFlowGameAdapter(
    private val config: GamingFlowConfig,
) : IGamePort {

    private val client = GamingFlowHttpClient(config)

    override suspend fun getAggregatorGames(): List<IGamePort.AggregatorGame> =
        client.listGames().map { it.toAggregatorGame() }

    override suspend fun getDemoUrl(
        gameSymbol: String,
        locale: Locale,
        platform: Platform,
        currency: Currency,
        lobbyUrl: String,
    ): String {
        val bankGroupId = config.bankGroupId(currency.value)

        client.setBankGroup(id = bankGroupId, currency = currency.value)

        val session = client.createDemoSession(
            gameId = gameSymbol,
            bankGroupId = bankGroupId,
            language = language(locale)
        )

        return launchUrl(session)
    }

    /**
     * Provisions the provider-side entities the session depends on, then opens the session.
     *
     * `BankGroup.Set` and `Player.Set` are idempotent upserts, so this runs on every launch instead
     * of tracking provisioning state locally. Our session token travels as `AlternativeId`: the
     * provider echoes it back as `sessionAlternativeId` on every Seamless API call, which is how an
     * inbound wallet request resolves our session.
     */
    override suspend fun getLaunchUrl(session: Session, lobbyUrl: String): String {
        val currency = session.currency.value
        val bankGroupId = config.bankGroupId(currency)
        val playerId = config.playerName(session.playerId.value, currency)

        client.setBankGroup(id = bankGroupId, currency = currency)
        client.setPlayer(id = playerId, bankGroupId = bankGroupId)

        val created = client.createSession(
            playerId = playerId,
            gameId = session.gameVariant.symbol.value,
            alternativeId = session.token.value,
            language = language(session.locale)
        )

        return launchUrl(created)
    }

    /** The provider serves each session on its own subdomain of the configured base host. Its own
     *  `SessionUrl` is deprecated — the domain behind it can change — so it is only a fallback. */
    private fun launchUrl(session: SessionResultDto): String =
        if (config.baseHost.isNotBlank()) "https://${session.sessionId}.${config.baseHost}/"
        else session.sessionUrl.ifBlank { error("GamingFlow returned no session URL") }

    /** Games only speak the languages below; anything else falls back to English. */
    private fun language(locale: Locale): String =
        locale.value.lowercase().takeIf { it in SUPPORTED_LANGUAGES } ?: DEFAULT_LANGUAGE

    private fun GameDto.toAggregatorGame(): IGamePort.AggregatorGame =
        IGamePort.AggregatorGame(
            symbol = id,
            name = name.ifBlank { id },
            providerName = sectionId,
            freeSpinEnable = FREEROUND_TAG in tags,
            freeChipEnable = false,
            jackpotEnable = false,
            demoEnable = NO_DEMO_TAG !in tags,
            bonusBuyEnable = false,
            locales = emptyList(),
            platforms = platforms(tags),
            playLines = linesCount.takeWhile { it.isDigit() }.toIntOrNull() ?: 0,
            tags = tags
        )

    /** A game is available on both platforms unless it is tagged as restricted to one. */
    private fun platforms(tags: List<String>): List<Platform> = when {
        DESKTOP_ONLY_TAG in tags -> listOf(Platform.DESKTOP)
        MOBILE_ONLY_TAG in tags -> listOf(Platform.MOBILE)
        else -> listOf(Platform.DESKTOP, Platform.MOBILE)
    }

    private companion object {
        const val FREEROUND_TAG = "FR"

        const val NO_DEMO_TAG = "NoD"

        const val DESKTOP_ONLY_TAG = "PC"

        const val MOBILE_ONLY_TAG = "mobile"

        const val DEFAULT_LANGUAGE = "en"

        val SUPPORTED_LANGUAGES = setOf("en", "fr", "de", "ru", "cn", "es", "pl", "it")
    }
}
