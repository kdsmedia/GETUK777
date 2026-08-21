package domain.service

import domain.exception.badrequest.UnsupportedPlatformException
import domain.exception.conflict.AggregatorNotActiveException
import domain.exception.conflict.CasinoGameNotActiveException
import domain.exception.conflict.CasinoProviderNotActiveException
import domain.exception.domainRequire
import domain.model.CasinoGameVariant
import domain.model.Platform
import domain.model.CasinoSession
import domain.vo.Currency
import domain.vo.Locale
import domain.vo.PlayerId
import domain.vo.CasinoSessionToken

object CasinoSessionFactory {

    private val DEFAULT_LOCALE = Locale("en")

    fun create(
        token: CasinoSessionToken,
        playerId: PlayerId,
        gameVariant: CasinoGameVariant,
        currency: Currency,
        locale: Locale,
        platform: Platform,
    ): CasinoSession {
        domainRequire(gameVariant.game.active) { CasinoGameNotActiveException() }
        domainRequire(gameVariant.game.provider.active) { CasinoProviderNotActiveException() }
        domainRequire(gameVariant.game.provider.aggregator.active) { AggregatorNotActiveException() }

        // Locale is a soft UI-language hint — if the game doesn't advertise the requested
        // one, fall back to English instead of refusing to launch.
        val resolvedLocale = if (gameVariant.supportsLocale(locale)) locale else DEFAULT_LOCALE

        domainRequire(gameVariant.supportsPlatform(platform)) { UnsupportedPlatformException(platform) }

        return CasinoSession(
            token = token,
            gameVariant = gameVariant,
            playerId = playerId,
            externalToken = null,
            currency = currency,
            locale = resolvedLocale,
            platform = platform,
        )
    }
}
