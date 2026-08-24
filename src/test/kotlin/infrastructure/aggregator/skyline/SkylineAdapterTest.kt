package infrastructure.aggregator.skyline

import domain.model.Platform
import domain.vo.Currency
import domain.vo.Locale
import domain.vo.PlayerId
import infrastructure.aggregator.skyline.adapter.SkylineFreespinAdapter
import infrastructure.aggregator.skyline.adapter.SkylineGameAdapter
import infrastructure.aggregator.skyline.client.SkylineJwt
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import support.TestFixtures

/**
 * Drives the adapters against a fake RGS so the wire contract is exercised end to end: the real CIO
 * client, real signing, real JSON.
 *
 * The fake behaves the way the live endpoint was observed to: it refuses a body that is not a token
 * signed with the shared secret, it answers with a signed token of its own, and it reports failure
 * as an `error` code sitting where the result would be — never as an HTTP status.
 */
class SkylineAdapterTest : FunSpec({

    val secret = "L!V4#nz"
    val apiKey = "test-api-key"

    val jwt = SkylineJwt(secret)

    val requests = mutableListOf<JsonObject>()
    val protocolViolations = mutableListOf<String>()

    var port = 0

    val server = embeddedServer(CIO, port = 0) {
        routing {
            post("/partner.php") {
                val raw = call.receiveText()

                val payload = jwt.decode(raw)
                if (payload == null) {
                    protocolViolations += "request body was not a token signed with our secret"
                    call.respondText("{}", ContentType.Application.Json)
                    return@post
                }

                requests += payload

                if (payload["apikey"]?.jsonPrimitive?.content != apiKey) {
                    protocolViolations += "apikey was ${payload["apikey"]}"
                }

                val result: JsonElement = when (payload["action"]?.jsonPrimitive?.content) {
                    // A bare ARRAY, not the documented {"games":[...]} object.
                    "game_list" -> Json.parseToJsonElement(GAME_LIST)

                    "game_launch" ->
                        if (payload["game"]?.jsonPrimitive?.content == UNKNOWN_GAME) {
                            error(code = 103, description = "incorrect game id")
                        } else {
                            buildJsonObject { put("launch_url", LAUNCH_URL) }
                        }

                    "bonus_award", "bonus_cancel" -> buildJsonObject { put("status", "ok") }

                    else -> error(code = 104, description = "incorrect action")
                }

                call.respondText(
                    text = jwt.encode(buildJsonObject { put("result", result) }),
                    contentType = ContentType.Application.Json,
                )
            }
        }
    }

    fun config(overrides: Map<String, Any> = emptyMap()) = SkylineConfig(
        mapOf(
            "apiUrl" to "http://127.0.0.1:$port/partner.php",
            "apiKey" to apiKey,
            "jwtSecret" to secret,
            "casino" to "cloud1638",
            "defaultCountry" to "UA",
            "defaultPlayerIp" to "1.0.0.0",
        ) + overrides
    )

    fun field(name: String): String? = requests.first()[name]?.jsonPrimitive?.content

    beforeSpec {
        server.start(wait = false)
        port = server.engine.resolvedConnectors().first().port
    }

    afterSpec {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
    }

    beforeTest {
        requests.clear()
        protocolViolations.clear()
    }

    afterTest {
        protocolViolations.shouldBeEmpty()
    }

    test("the catalogue reads the bare array the vendor actually sends") {
        val games = SkylineGameAdapter(config()).getAggregatorGames()

        games.map { it.symbol } shouldContainExactly listOf("slp1", "kog8")
        games.map { it.name } shouldContainExactly listOf("Slap Club", "King of Slots")
    }

    test("every game is filed under the one configured provider") {
        val games = SkylineGameAdapter(config(mapOf("providerName" to "Skyline Software"))).getAggregatorGames()

        games.map { it.providerName }.distinct() shouldContainExactly listOf("Skyline Software")
    }

    test("category becomes a tag only when the feed carries one") {
        val games = SkylineGameAdapter(config()).getAggregatorGames()

        // The live feed sends no category at all; the documented example does.
        games.first { it.symbol == "slp1" }.tags.shouldBeEmpty()
        games.first { it.symbol == "kog8" }.tags shouldContainExactly listOf("slot")
    }

    test("a real launch sends our own session token and the player's id") {
        val session = TestFixtures.session(
            variant = TestFixtures.gameVariant(symbol = "slp1"),
            currency = "UAH",
            locale = "uk",
            platform = Platform.MOBILE,
            playerId = "42",
            token = "our-token-abc",
        )

        val launch = SkylineGameAdapter(config()).getLaunchUrl(session, "https://prematch.win/casino")

        launch.url shouldBe LAUNCH_URL
        // Nothing is persisted as an external token: the vendor mints no id of its own and every
        // callback comes back carrying the token we just sent.
        launch.externalToken shouldBe null

        field("action") shouldBe "game_launch"
        field("session") shouldBe "our-token-abc"
        field("player_id") shouldBe "42"
        field("game") shouldBe "slp1"
        field("currency") shouldBe "UAH"
        field("language") shouldBe "uk"
        field("platform") shouldBe "mobile"
        field("casino") shouldBe "cloud1638"
        field("lobby") shouldBe "https://prematch.win/casino"
        field("player_ip") shouldBe "1.0.0.0"
        field("country") shouldBe "UA"
        // Absent, not false: their contract reads any present `demo` as a demo launch.
        requests.first().containsKey("demo") shouldBe false
    }

    test("a blank optional is dropped rather than sent empty") {
        val session = TestFixtures.session(variant = TestFixtures.gameVariant(symbol = "slp1"))

        SkylineGameAdapter(config()).getLaunchUrl(session, "")

        // Unset here, and the vendor echoes an empty value straight into the launch URL.
        requests.first().containsKey("lobby") shouldBe false
        requests.first().containsKey("cassier") shouldBe false
    }

    test("a demo launch is flagged and never names a real player") {
        SkylineGameAdapter(config()).getDemoUrl(
            gameSymbol = "slp1",
            locale = Locale("en"),
            platform = Platform.DESKTOP,
            currency = Currency("UAH"),
            lobbyUrl = "https://prematch.win/casino",
        )

        field("demo") shouldBe "true"
        field("platform") shouldBe "desktop"
    }

    test("a free-round grant is scoped to its one game and priced in minor units") {
        SkylineFreespinAdapter(config()).create(
            presetValue = emptyMap(),
            referenceId = "fs-2026-01",
            playerId = PlayerId("42"),
            gameSymbol = "slp1",
            currency = Currency("UAH"),
            startAt = LocalDateTime(2026, 8, 24, 9, 5, 0),
            endAt = LocalDateTime(2026, 8, 31, 23, 59, 59),
            // 4.50 UAH in the wallet's nano units.
            spinAmount = 4_500_000_000,
            spinCount = 20,
        )

        field("action") shouldBe "bonus_award"
        field("bonus") shouldBe "fs-2026-01"
        field("casino") shouldBe "cloud1638"
        field("bet") shouldBe "450"
        field("quantity") shouldBe "20"
        field("player_id") shouldBe "42"
        field("currency") shouldBe "UAH"
        field("filter") shouldBe "only"
        field("games") shouldBe "slp1"
        // Their format, which LocalDateTime.toString() does not produce.
        field("start") shouldBe "2026-08-24 09:05:00"
        field("expiration") shouldBe "2026-08-31 23:59:59"
    }

    test("cancelling a grant names it by our own reference") {
        SkylineFreespinAdapter(config()).cancel("fs-2026-01")

        field("action") shouldBe "bonus_cancel"
        field("bonus") shouldBe "fs-2026-01"
    }

    test("an error in the payload fails the call even though the status is 200") {
        val session = TestFixtures.session(variant = TestFixtures.gameVariant(symbol = UNKNOWN_GAME))

        shouldThrowAny { SkylineGameAdapter(config()).getLaunchUrl(session, "") }
    }

    test("a reply that is not signed with our secret is never trusted") {
        shouldThrowAny { SkylineGameAdapter(config(mapOf("jwtSecret" to "wrong"))).getAggregatorGames() }

        // The fake saw an unverifiable request, which is the same failure seen from the other side.
        protocolViolations shouldContainExactly listOf("request body was not a token signed with our secret")
        protocolViolations.clear()
    }
})

private fun error(code: Int, description: String): JsonObject = buildJsonObject {
    put("error", code)
    put("description", description)
}

private const val LAUNCH_URL = "https://slapclub.example/?session=our-token-abc"

private const val UNKNOWN_GAME = "nope"

/** `slp1` mirrors the live feed exactly (no category); `kog8` is the documented example. */
private const val GAME_LIST = """[
  {"game_id":"slp1","game_title":"Slap Club","images":[{"width":300,"height":300,"url":"https://cdn/slp1.png"}]},
  {"game_id":"kog8","game_title":"King of Slots","category":"slot","images":[]}
]"""
