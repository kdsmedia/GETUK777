package domain.service

import domain.exception.badrequest.UnsupportedPlatformException
import domain.exception.conflict.AggregatorNotActiveException
import domain.exception.conflict.CasinoGameNotActiveException
import domain.exception.conflict.CasinoProviderNotActiveException
import domain.model.Aggregator
import domain.model.Platform
import domain.vo.Currency
import domain.vo.Locale
import domain.vo.PlayerId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import domain.vo.CasinoSessionToken
import support.TestFixtures

class CasinoSessionFactoryTest : FunSpec({

    fun call(
        game: domain.model.CasinoGame = TestFixtures.game(),
        locale: String = "en",
        platform: Platform = Platform.DESKTOP,
        aggregator: Aggregator = game.provider.aggregator,
    ) = CasinoSessionFactory.create(
        token = CasinoSessionToken("t"),
        playerId = PlayerId("p"),
        gameVariant = TestFixtures.gameVariant(
            game = game,
            locales = listOf(Locale("en")),
            platforms = listOf(Platform.DESKTOP, Platform.MOBILE),
        ),
        aggregator = aggregator,
        currency = Currency("USD"),
        locale = Locale(locale),
        platform = platform,
    )

    test("happy path builds valid session") {
        val session = call()
        session.token shouldBe CasinoSessionToken("t")
        session.currency shouldBe Currency("USD")
    }

    test("inactive game throws") {
        val game = TestFixtures.game(active = false)
        shouldThrow<CasinoGameNotActiveException> { call(game = game) }
    }

    test("inactive provider throws") {
        val provider = TestFixtures.provider(active = false)
        val game = TestFixtures.game(provider = provider)
        shouldThrow<CasinoProviderNotActiveException> { call(game = game) }
    }

    test("inactive aggregator throws") {
        val aggregator = TestFixtures.aggregator(active = false)
        val provider = TestFixtures.provider(aggregator = aggregator)
        val game = TestFixtures.game(provider = provider)
        shouldThrow<AggregatorNotActiveException> { call(game = game) }
    }

    test("the serving aggregator is checked, not the provider's") {
        // A game the provider's own aggregator does not carry is served by another one. Rejecting
        // it because the PROVIDER's aggregator is off would kill every fallback launch.
        val provider = TestFixtures.provider(aggregator = TestFixtures.aggregator(active = false))
        val game = TestFixtures.game(provider = provider)
        val serving = TestFixtures.aggregator(identity = "other", integration = "GAMINGFLOW")

        call(game = game, aggregator = serving).token shouldBe CasinoSessionToken("t")
    }

    test("an inactive serving aggregator throws even when the provider's is live") {
        val serving = TestFixtures.aggregator(identity = "other", integration = "GAMINGFLOW", active = false)
        shouldThrow<AggregatorNotActiveException> { call(aggregator = serving) }
    }

    test("unsupported locale falls back to en") {
        call(locale = "fr").locale shouldBe Locale("en")
    }

    test("unsupported platform throws") {
        shouldThrow<UnsupportedPlatformException> { call(platform = Platform.DOWNLOAD) }
    }
})
