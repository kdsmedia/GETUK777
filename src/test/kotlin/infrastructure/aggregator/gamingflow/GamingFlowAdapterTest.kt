package infrastructure.aggregator.gamingflow

import domain.exception.conflict.FreespinNotSupportedException
import domain.model.Platform
import domain.vo.Currency
import domain.vo.Locale
import infrastructure.aggregator.gamingflow.adapter.GamingFlowFreespinAdapter
import infrastructure.aggregator.gamingflow.adapter.GamingFlowGameAdapter
import infrastructure.aggregator.gamingflow.client.GamingFlowSigner
import infrastructure.aggregator.gamingflow.client.dto.GamingFlowRpcError
import infrastructure.aggregator.gamingflow.client.dto.GamingFlowRpcRequest
import infrastructure.aggregator.gamingflow.client.dto.GamingFlowRpcResponse
import io.kotest.assertions.throwables.shouldThrow
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Drives both adapters against a fake provider so the wire contract is exercised end to end: the
 * real CIO client, real signing, real JSON-RPC envelopes. The fake re-derives every signature from
 * the raw request body and refuses to guess — a signing regression fails the test rather than
 * silently passing.
 */
class GamingFlowAdapterTest : FunSpec({

    val keyId = "myKey123"
    val keyValue = "s3cr3t"
    val casinoId = "2239"

    val calls = mutableListOf<Pair<String, JsonObject>>()
    val results = mutableMapOf<String, JsonElement>()
    val protocolViolations = mutableListOf<String>()
    var rpcError: GamingFlowRpcError? = null

    val wireJson = Json { explicitNulls = false }

    val server = embeddedServer(CIO, port = 0) {
        routing {
            post("/v1/signed/") {
                val body = call.receiveText()
                val headers = call.request.headers

                val nonce = headers[HEADER_NONCE]
                val timestamp = headers[HEADER_TIMESTAMP]?.toLongOrNull()
                val subject = headers[HEADER_SUBJECT]
                val signature = headers[HEADER_SIGNATURE]

                if (headers[HEADER_ACCEPT] != ContentType.Application.Json.toString()) {
                    protocolViolations += "Accept header was ${headers[HEADER_ACCEPT]}"
                }
                if (subject != "casino:$casinoId") {
                    protocolViolations += "X-Subject was $subject"
                }
                if (nonce == null || timestamp == null) {
                    protocolViolations += "missing nonce/timestamp"
                } else {
                    val expected = "$keyId=" + hmacHex(keyValue, body, nonce, timestamp)
                    if (signature != expected) {
                        protocolViolations += "X-Signature was $signature, expected $expected"
                    }
                }

                val request = wireJson.decodeFromString(GamingFlowRpcRequest.serializer(), body)

                if (request.jsonrpc != "2.0") protocolViolations += "jsonrpc was ${request.jsonrpc}"
                if (request.id <= 0) protocolViolations += "id was ${request.id}"

                calls += request.method to request.params

                val response = rpcError
                    ?.let { GamingFlowRpcResponse(id = request.id, error = it) }
                    ?: GamingFlowRpcResponse(
                        id = request.id,
                        result = results[request.method] ?: JsonObject(emptyMap())
                    )

                call.respondText(
                    text = wireJson.encodeToString(GamingFlowRpcResponse.serializer(), response),
                    contentType = ContentType.Application.Json
                )
            }
        }
    }

    var port = 0

    fun config(overrides: Map<String, Any> = emptyMap()) = GamingFlowConfig(
        mapOf(
            "apiUrl" to "http://127.0.0.1:$port/v1/signed/",
            "casinoId" to casinoId,
            "keyId" to keyId,
            "keyValue" to keyValue,
            "baseHost" to "gamix.party",
            "bankGroupPrefix" to "1638",
        ) + overrides
    )

    fun params(method: String): JsonObject =
        calls.first { it.first == method }.second

    fun stringParam(method: String, key: String): String? =
        params(method)[key]?.jsonPrimitive?.content

    beforeSpec {
        server.start(wait = false)
        port = server.engine.resolvedConnectors().first().port
    }

    afterSpec {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
    }

    beforeTest {
        calls.clear()
        results.clear()
        protocolViolations.clear()
        rpcError = null
    }

    // Every call in every test must carry a valid signature, subject and JSON-RPC envelope.
    afterTest {
        protocolViolations.shouldBeEmpty()
    }

    test("signature matches an independently computed vector") {
        // Computed with Python hmac/struct from the provider's documented algorithm:
        // HMAC_SHA256(big_endian_uint64(nonce) + big_endian_uint32(timestamp) + body, key).
        // The nonce is deliberately above Long.MAX_VALUE — it is an UNSIGNED 64-bit number.
        val body = """{"jsonrpc":"2.0","method":"Game.List","id":1,"params":{}}"""

        val signature = GamingFlowSigner(keyId = keyId, keyValue = keyValue)
            .sign(body = body, nonce = "10080051040501637901", timestamp = 1_761_886_070L)

        signature shouldBe
            "myKey123=9cb43d7c2d88252f9e452f2a281dd6e1b5f7ea5dcd56c049ae3db4e563232add"

        protocolViolations.clear()
    }

    test("nonces never repeat") {
        val signer = GamingFlowSigner(keyId = keyId, keyValue = keyValue)

        val nonces = List(1_000) { signer.nextNonce() }

        nonces.toSet().size shouldBe nonces.size
    }

    test("Game.List maps the provider catalog") {
        results["Game.List"] = Json.parseToJsonElement(
            """
            {
              "Games": [
                {
                  "Id": "magic_maze_html",
                  "Name": "Magic maze slot",
                  "Description": "10 Lines, L/R, Respin",
                  "SectionId": "mazegaming",
                  "Type": "slots",
                  "Tags": ["FR"],
                  "Format": "html",
                  "LinesCount": "15",
                  "BaseBet": 15
                },
                {
                  "Id": "ways_game",
                  "Name": "Ways Game",
                  "SectionId": "waysgaming",
                  "Type": "slots",
                  "Tags": ["NoD", "PC"],
                  "Format": "html",
                  "LinesCount": "243-3125w",
                  "BaseBet": 20
                },
                {
                  "Id": "pocket_game",
                  "Name": "",
                  "SectionId": "pocketgaming",
                  "Type": "slots",
                  "Tags": ["mobile"],
                  "Format": "html"
                }
              ]
            }
            """.trimIndent()
        )

        val games = GamingFlowGameAdapter(config()).getAggregatorGames()

        calls.map { it.first } shouldContainExactly listOf("Game.List")
        games.map { it.symbol } shouldContainExactly
            listOf("magic_maze_html", "ways_game", "pocket_game")

        val magicMaze = games[0]
        magicMaze.name shouldBe "Magic maze slot"
        magicMaze.providerName shouldBe "mazegaming"
        magicMaze.freeSpinEnable shouldBe true
        magicMaze.demoEnable shouldBe true
        magicMaze.playLines shouldBe 15
        magicMaze.platforms shouldContainExactly listOf(Platform.DESKTOP, Platform.MOBILE)
        magicMaze.tags shouldContainExactly listOf("FR")

        val waysGame = games[1]
        waysGame.freeSpinEnable shouldBe false
        // "NoD" — no demo support; "PC" — desktop only.
        waysGame.demoEnable shouldBe false
        waysGame.platforms shouldContainExactly listOf(Platform.DESKTOP)
        // A ways-based game reports a range; we keep the leading number.
        waysGame.playLines shouldBe 243

        val pocketGame = games[2]
        pocketGame.platforms shouldContainExactly listOf(Platform.MOBILE)
        // Blank name falls back to the id so the catalog never shows an empty row.
        pocketGame.name shouldBe "pocket_game"
        pocketGame.playLines shouldBe 0
    }

    test("launch upserts the bank group and player, then opens the session") {
        results["Session.Create"] = Json.parseToJsonElement(
            """{"SessionId": "n5vpp2xp406c8f5", "SessionUrl": "https://n5vpp2xp406c8f5.stale.example/"}"""
        )

        val session = support.TestFixtures.session(
            currency = "UAH",
            locale = "es",
            playerId = "player_1",
            token = "token_abc"
        )

        val launch = GamingFlowGameAdapter(config()).getLaunchUrl(session, lobbyUrl = "https://lobby")

        // Order matters: the session references a player that references a bank group.
        calls.map { it.first } shouldContainExactly
            listOf("BankGroup.Set", "Player.Set", "Session.Create")

        stringParam("BankGroup.Set", "Id") shouldBe "1638_UAH"
        stringParam("BankGroup.Set", "Currency") shouldBe "UAH"

        // A player is bound to one bank group for life, so it is minted per currency.
        stringParam("Player.Set", "Id") shouldBe "player_1_UAH"
        stringParam("Player.Set", "BankGroupId") shouldBe "1638_UAH"

        stringParam("Session.Create", "PlayerId") shouldBe "player_1_UAH"
        stringParam("Session.Create", "GameId") shouldBe session.gameVariant.symbol.value
        stringParam("Session.Create", "RestorePolicy") shouldBe "Restore"
        stringParam("Session.Create", "BaseHost") shouldBe "gamix.party"
        // Our own token — the provider echoes it back on every Seamless API call.
        stringParam("Session.Create", "AlternativeId") shouldBe "token_abc"
        params("Session.Create")["Params"]!!.jsonObject["language"]!!.jsonPrimitive.content shouldBe "es"

        // Composed from BaseHost, not from the deprecated SessionUrl the provider returned.
        launch.url shouldBe "https://n5vpp2xp406c8f5.gamix.party/"

        // The provider id is carried back so the session can be stored against it.
        launch.externalToken shouldBe "n5vpp2xp406c8f5"
    }

    test("an unsupported game language falls back to English") {
        results["Session.Create"] = Json.parseToJsonElement("""{"SessionId": "abc"}""")

        GamingFlowGameAdapter(config()).getLaunchUrl(
            session = support.TestFixtures.session(locale = "uk"),
            lobbyUrl = "https://lobby"
        )

        params("Session.Create")["Params"]!!.jsonObject["language"]!!.jsonPrimitive.content shouldBe "en"
    }

    test("launch falls back to the provider URL when no base host is configured") {
        results["Session.Create"] = Json.parseToJsonElement(
            """{"SessionId": "abc", "SessionUrl": "https://abc.provider.example/"}"""
        )

        val launch = GamingFlowGameAdapter(config(mapOf("baseHost" to ""))).getLaunchUrl(
            session = support.TestFixtures.session(),
            lobbyUrl = "https://lobby"
        )

        params("Session.Create")["BaseHost"] shouldBe null
        launch.url shouldBe "https://abc.provider.example/"
    }

    test("demo upserts the bank group and opens a demo session") {
        results["Session.CreateDemo"] = Json.parseToJsonElement("""{"SessionId": "demo123"}""")

        val url = GamingFlowGameAdapter(config()).getDemoUrl(
            gameSymbol = "magic_maze_html",
            locale = Locale("de"),
            platform = Platform.MOBILE,
            currency = Currency("EUR"),
            lobbyUrl = "https://lobby"
        )

        calls.map { it.first } shouldContainExactly listOf("BankGroup.Set", "Session.CreateDemo")

        stringParam("BankGroup.Set", "Id") shouldBe "1638_EUR"
        stringParam("Session.CreateDemo", "GameId") shouldBe "magic_maze_html"
        stringParam("Session.CreateDemo", "BankGroupId") shouldBe "1638_EUR"
        stringParam("Session.CreateDemo", "StartBalance") shouldBe "10000"
        params("Session.CreateDemo")["Params"]!!.jsonObject["language"]!!.jsonPrimitive.content shouldBe "de"

        // A demo session never reaches the Seamless API, so no player is provisioned.
        url shouldBe "https://demo123.gamix.party/"
    }

    test("freespin preset reports free-round support off the FR tag") {
        results["Game.List"] = Json.parseToJsonElement(
            """
            {"Games": [
              {"Id": "magic_maze_html", "Name": "Magic maze", "Tags": ["FR"], "LinesCount": "15", "BaseBet": 15},
              {"Id": "plain_game", "Name": "Plain", "Tags": [], "LinesCount": "10", "BaseBet": 10}
            ]}
            """.trimIndent()
        )

        val adapter = GamingFlowFreespinAdapter(config())

        adapter.getPreset("magic_maze_html") shouldBe
            mapOf("freeroundSupported" to true, "baseBet" to 15, "linesCount" to "15")

        adapter.getPreset("plain_game") shouldBe
            mapOf("freeroundSupported" to false, "baseBet" to 10, "linesCount" to "10")
    }

    test("freespin create registers the bonus id") {
        GamingFlowFreespinAdapter(config()).create(
            presetValue = mapOf("freeroundSupported" to true),
            referenceId = "WelcomeBonusJan2026",
            playerId = domain.vo.PlayerId("player_1"),
            gameSymbol = "magic_maze_html",
            currency = Currency("UAH"),
            startAt = LocalDateTime(2026, 8, 7, 0, 0),
            endAt = LocalDateTime(2026, 9, 7, 0, 0),
            spinAmount = 100,
            spinCount = 10
        )

        // The provider stores nothing but the id — count, stake and window stay on our side.
        calls.map { it.first } shouldContainExactly listOf("Bonus.Set")
        stringParam("Bonus.Set", "Id") shouldBe "WelcomeBonusJan2026"
    }

    test("freespin create is refused for a game without free-round support") {
        shouldThrow<FreespinNotSupportedException> {
            GamingFlowFreespinAdapter(config()).create(
                presetValue = mapOf("freeroundSupported" to false),
                referenceId = "WelcomeBonusJan2026",
                playerId = domain.vo.PlayerId("player_1"),
                gameSymbol = "plain_game",
                currency = Currency("UAH"),
                startAt = LocalDateTime(2026, 8, 7, 0, 0),
                endAt = LocalDateTime(2026, 9, 7, 0, 0),
                spinAmount = 100,
                spinCount = 10
            )
        }

        calls.shouldBeEmpty()
    }

    test("freespin cancel calls nothing — the provider holds no free-round state") {
        GamingFlowFreespinAdapter(config()).cancel("WelcomeBonusJan2026")

        calls.shouldBeEmpty()
    }

    test("a JSON-RPC error surfaces instead of being swallowed") {
        rpcError = GamingFlowRpcError(code = 10802, message = "BONUS_ID_INVALID")

        val failure = shouldThrow<IllegalStateException> {
            GamingFlowGameAdapter(config()).getLaunchUrl(
                session = support.TestFixtures.session(),
                lobbyUrl = "https://lobby"
            )
        }

        failure.message shouldBe "GamingFlow BankGroup.Set failed: 10802 BONUS_ID_INVALID"
    }

    test("player name survives a round trip through the provider's id format") {
        val config = config()

        config.playerIdOf(config.playerName("player_1", "uah")) shouldBe "player_1"
        config.playerIdOf(config.playerName("player_1_extra", "UAH")) shouldBe "player_1_extra"
    }
})

private const val HEADER_NONCE = "X-Nonce"

private const val HEADER_SIGNATURE = "X-Signature"

private const val HEADER_SUBJECT = "X-Subject"

private const val HEADER_TIMESTAMP = "X-Timestamp"

private const val HEADER_ACCEPT = "Accept"

/** Independent re-implementation of the provider's documented signing algorithm. */
private fun hmacHex(key: String, body: String, nonce: String, timestamp: Long): String {
    val prefix = ByteBuffer.allocate(12)
        .putLong(java.lang.Long.parseUnsignedLong(nonce))
        .putInt(timestamp.toInt())
        .array()

    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    mac.update(prefix)
    mac.update(body.toByteArray(Charsets.UTF_8))

    return mac.doFinal().joinToString("") { "%02x".format(it) }
}
