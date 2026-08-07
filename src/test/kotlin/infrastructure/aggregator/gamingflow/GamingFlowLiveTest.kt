package infrastructure.aggregator.gamingflow

import domain.model.Platform
import domain.vo.Currency
import domain.vo.Locale
import infrastructure.aggregator.gamingflow.adapter.GamingFlowFreespinAdapter
import infrastructure.aggregator.gamingflow.adapter.GamingFlowGameAdapter
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.LocalDateTime

/**
 * Smoke test against the provider's live API. Disabled unless `GAMINGFLOW_KEY_ID` and
 * `GAMINGFLOW_KEY_VALUE` are exported — credentials never live in the repo.
 *
 * Writes are scoped to throwaway ids: a bank group's currency and a player's bank group are both
 * write-once at the provider, so a test must never claim a name production will want later.
 */
class GamingFlowLiveTest : FunSpec({

    val keyId = System.getenv("GAMINGFLOW_KEY_ID")
    val keyValue = System.getenv("GAMINGFLOW_KEY_VALUE")
    val live = !keyId.isNullOrBlank() && !keyValue.isNullOrBlank()

    fun config() = GamingFlowConfig(
        mapOf(
            "apiUrl" to "https://customer.gaming-flow.org/v1/signed/",
            "casinoId" to (System.getenv("GAMINGFLOW_CASINO_ID") ?: "2239"),
            "keyId" to keyId.orEmpty(),
            "keyValue" to keyValue.orEmpty(),
            "baseHost" to "gamix.party",
            "bankGroupPrefix" to "smoketest",
        )
    )

    test("Game.List returns the live catalog").config(enabled = live) {
        val games = GamingFlowGameAdapter(config()).getAggregatorGames()

        println("games: ${games.size}")
        println("providers: ${games.map { it.providerName }.distinct().sorted()}")
        println("freespin-capable: ${games.count { it.freeSpinEnable }}")
        println("demo-capable: ${games.count { it.demoEnable }}")
        println("desktop-only: ${games.count { it.platforms == listOf(Platform.DESKTOP) }}")
        println("mobile-only: ${games.count { it.platforms == listOf(Platform.MOBILE) }}")
        println("sample: " + games.take(3).joinToString { "${it.symbol}/${it.providerName}/${it.playLines}" })
    }

    test("Session.CreateDemo returns a playable URL").config(enabled = live) {
        val url = GamingFlowGameAdapter(config()).getDemoUrl(
            gameSymbol = System.getenv("GAMINGFLOW_GAME") ?: "victorious_touch",
            locale = Locale("en"),
            platform = Platform.DESKTOP,
            currency = Currency("UAH"),
            lobbyUrl = "https://prematch.win/casino"
        )

        println("demo url: $url")
    }

    test("Bonus.Set registers a free-round bonus").config(enabled = live) {
        val adapter = GamingFlowFreespinAdapter(config())

        val gameSymbol = System.getenv("GAMINGFLOW_GAME") ?: "victorious_touch"
        val preset = adapter.getPreset(gameSymbol)

        println("preset($gameSymbol): $preset")

        adapter.create(
            presetValue = preset,
            referenceId = "smoketest-bonus-1",
            playerId = domain.vo.PlayerId("smoketest_player"),
            gameSymbol = gameSymbol,
            currency = Currency("UAH"),
            startAt = LocalDateTime(2026, 8, 7, 0, 0),
            endAt = LocalDateTime(2026, 9, 7, 0, 0),
            spinAmount = 100,
            spinCount = 10
        )

        println("bonus registered")
    }

    test("Session.Create reaches the Seamless API").config(enabled = live) {
        // Expected to fail until the Seamless API v2 webhook exists: the provider calls getBalance
        // synchronously while creating the session and answers 10505 when it gets nothing back.
        runCatching {
            GamingFlowGameAdapter(config()).getLaunchUrl(
                session = support.TestFixtures.session(
                    variant = support.TestFixtures.gameVariant(
                        symbol = System.getenv("GAMINGFLOW_GAME") ?: "victorious_touch"
                    ),
                    currency = "UAH",
                    playerId = "smoketest_player",
                    token = "smoketest-token-1"
                ),
                lobbyUrl = "https://prematch.win/casino"
            )
        }
            .onSuccess { println("launch url: $it") }
            .onFailure { println("launch failed: ${it.message}") }
    }
})
