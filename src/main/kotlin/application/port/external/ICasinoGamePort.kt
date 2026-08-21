package application.port.external

import domain.model.Freespin
import domain.model.Platform
import domain.model.CasinoSession
import domain.vo.Currency
import domain.vo.Locale

interface ICasinoGamePort {
    data class AggregatorGame(
        val symbol: String,
        val name: String,
        val providerName: String,
        val freeSpinEnable: Boolean,
        val freeChipEnable: Boolean,
        val jackpotEnable: Boolean,
        val demoEnable: Boolean,
        val bonusBuyEnable: Boolean,
        val locales: List<Locale>,
        val platforms: List<Platform>,
        val playLines: Int = 0,
        val tags: List<String> = emptyList(),
    )

    /**
     * A launch URL plus, when the provider mints its own session identifier, that identifier.
     * It is persisted as `CasinoSession.externalToken` so an inbound webhook can resolve the session
     * by the provider's id and not only by the token we handed out.
     */
    data class Launch(
        val url: String,
        val externalToken: String? = null,
    )

    suspend fun getAggregatorGames(): List<AggregatorGame>

    suspend fun getDemoUrl(
        gameSymbol: String,
        locale: Locale,
        platform: Platform,
        currency: Currency,
        lobbyUrl: String,
    ): String

    /**
     * [freespin] is the grant the player is about to play through, when there is one. Providers
     * that attach free rounds at session creation need it here — the round is decided when the
     * session opens, not when the first spin arrives.
     */
    suspend fun getLaunchUrl(session: CasinoSession, lobbyUrl: String, freespin: Freespin? = null): Launch
}