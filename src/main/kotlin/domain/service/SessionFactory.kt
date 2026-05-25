package domain.service

import domain.exception.badrequest.UnsupportedPlatformException
import domain.exception.conflict.AggregatorNotActiveException
import domain.exception.conflict.GameNotActiveException
import domain.exception.conflict.ProviderNotActiveException
import domain.exception.domainRequire
import domain.model.GameVariant
import domain.model.Platform
import domain.model.Session
import domain.vo.Currency
import domain.vo.Locale
import domain.vo.PlayerId
import domain.vo.SessionToken

object SessionFactory {

    private val DEFAULT_LOCALE = Locale("en")

    fun create(
        token: SessionToken,
        playerId: PlayerId,
        gameVariant: GameVariant,
        currency: Currency,
        locale: Locale,
        platform: Platform,
    ): Session {
        domainRequire(gameVariant.game.active) { GameNotActiveException() }
        domainRequire(gameVariant.game.provider.active) { ProviderNotActiveException() }
        domainRequire(gameVariant.game.provider.aggregator.active) { AggregatorNotActiveException() }

        // Locale is a soft UI-language hint — if the game doesn't advertise the requested
        // one, fall back to English instead of refusing to launch.
        val resolvedLocale = if (gameVariant.supportsLocale(locale)) locale else DEFAULT_LOCALE

        domainRequire(gameVariant.supportsPlatform(platform)) { UnsupportedPlatformException(platform) }

        return Session(
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
