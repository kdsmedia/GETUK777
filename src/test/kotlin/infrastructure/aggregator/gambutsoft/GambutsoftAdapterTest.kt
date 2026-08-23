package infrastructure.aggregator.gambutsoft

import application.port.external.ICasinoGamePort
import domain.model.Platform
import domain.vo.Currency
import domain.vo.Locale
import infrastructure.aggregator.gambutsoft.adapter.GambutsoftGameAdapter
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import support.TestFixtures
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Drives the adapter against a fake vendor so the wire contract is exercised end to end: the real
 * CIO client, real signing, real JSON. The fake re-derives the signature from the raw request body
 * and refuses to guess — a body that is re-encoded anywhere between signing and sending fails the
 * test rather than silently passing.
 */
class GambutsoftAdapterTest : FunSpec({

    val secretKey = "s3cr3t"
    val userId = "6b218460-6ed9-4a97-8ff6-fc35791b9ae6"

    val catalogueRequests = mutableListOf<Pair<String, String>>()
    val openGameRequests = mutableListOf<JsonObject>()
    val protocolViolations = mutableListOf<String>()

    fun hmacHex(body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey.toByteArray(), "HmacSHA256"))
        return mac.doFinal(body.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun game(id: String, title: String, provider: String, enabled: Boolean = true) =
        """{"id":"$id","isEnabled":$enabled,"title":"$title","imageUrl":"https://cdn/$id.png",""" +
            """"category":"Slot","provider":"$provider"}"""

    val catalogues = mapOf(
        "UAH" to listOf(
            game("black:pragmatic:1005", "Club Tropicana", "pragmatic"),
            game("black:pg:1024", "Fortune Tiger", "PG Soft"),
            game("black:apollo:1049", "Gladorius", "apollo", enabled = false),
        ),
        "USD" to listOf(
            // Same game in a second currency: the union must not duplicate it.
            game("black:pragmatic:1005", "Club Tropicana", "pragmatic"),
            game("black:sg:389", "88 Fortunes", ""),
        ),
    )

    val server = embeddedServer(CIO, port = 0) {
        routing {
            post("/auth/login") {
                if (call.request.contentType().withoutParameters() != ContentType.Application.FormUrlEncoded) {
                    protocolViolations += "login content type was ${call.request.contentType()}"
                }

                val form = call.receiveParameters()
                if (form["login"] != "Client1Stage" || form["password"] != "pa55") {
                    protocolViolations += "login credentials were ${form["login"]}/${form["password"]}"
                }

                call.respondText(
                    text = """{"accessToken":"jwt-access","refreshToken":"jwt-refresh","user":{""" +
                        """"id":"$userId","login":"Client1Stage","role":"User",""" +
                        """"currency":{"code":"USD","digits":2},"count":"0",""" +
                        """"currencies":[{"currency":{"code":"UAH","digits":2},"count":"2220"}]}}""",
                    contentType = ContentType.Application.Json,
                )
            }

            get("/users/{userId}/getUserGames/{currency}") {
                if (call.request.headers["Authorization"] != "Bearer jwt-access") {
                    protocolViolations += "catalogue authorization was ${call.request.headers["Authorization"]}"
                }

                val requestedUser = call.parameters["userId"].orEmpty()
                val currency = call.parameters["currency"].orEmpty()
                catalogueRequests += requestedUser to currency

                val games = catalogues[currency]
                if (games == null) {
                    // The vendor answers 400 for a currency the account does not hold.
                    call.respondText(
                        text = """{"error":"unsupported currency"}""",
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.BadRequest,
                    )
                    return@get
                }

                call.respondText(text = games.joinToString(",", "[", "]"), contentType = ContentType.Application.Json)
            }

            post("/games/openGame") {
                val body = call.receiveText()

                // Answers exactly as the vendor does, so a signing regression surfaces as a failed
                // launch rather than as a passing test.
                if (call.request.headers["X-Signature"] != hmacHex(body)) {
                    call.respondText(
                        text = """{"code":401,"error":"unauthorized","message":"invalid signature","status":"fail"}""",
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.Unauthorized,
                    )
                    return@post
                }

                openGameRequests += Json.parseToJsonElement(body).jsonObject

                call.respondText(
                    text = """{"status":"success","error":"","content":{"game":{""" +
                        """"url":"https://cdn01.example/?session=69581232_abc"},""" +
                        """"gameRes":{"sessionId":"69581232"}}}""",
                    contentType = ContentType.Application.Json,
                )
            }
        }
    }

    var port = 0

    fun config(overrides: Map<String, Any> = emptyMap()) = GambutsoftConfig(
        mapOf(
            "officeApiUrl" to "http://127.0.0.1:$port",
            "clientApiUrl" to "http://127.0.0.1:$port",
            "login" to "Client1Stage",
            "password" to "pa55",
            "secretKey" to secretKey,
            "userId" to userId,
            "callbackUrl" to "https://webhook.prematch.win/api/webhook/gambutsoft",
            "exitUrl" to "https://prematch.win/casino",
        ) + overrides
    )

    fun adapter(overrides: Map<String, Any> = emptyMap()) = GambutsoftGameAdapter(config(overrides))

    fun openGameField(name: String): String? = openGameRequests.first()[name]?.jsonPrimitive?.content

    beforeSpec {
        server.start(wait = false)
        port = server.engine.resolvedConnectors().first().port
    }

    afterSpec {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
    }

    beforeTest {
        catalogueRequests.clear()
        openGameRequests.clear()
        protocolViolations.clear()
    }

    afterTest {
        protocolViolations.shouldBeEmpty()
    }

    test("the catalogue is the union of every configured currency, deduplicated") {
        val games = adapter(mapOf("catalogCurrencies" to listOf("UAH", "USD"))).getAggregatorGames()

        catalogueRequests shouldContainExactly listOf(userId to "UAH", userId to "USD")
        games.map { it.symbol } shouldContainExactly listOf(
            "black:pragmatic:1005",
            "black:pg:1024",
            "black:sg:389",
        )
    }

    test("a game the vendor has disabled is left out of the feed") {
        val games = adapter(mapOf("catalogCurrencies" to listOf("UAH"))).getAggregatorGames()

        games.map { it.symbol }.contains("black:apollo:1049") shouldBe false
    }

    test("without a configured currency list the login response decides") {
        adapter().getAggregatorGames()

        catalogueRequests shouldContainExactly listOf(userId to "UAH")
    }

    test("a currency the account does not hold fails loudly instead of syncing a short catalogue") {
        shouldThrowAny { adapter(mapOf("catalogCurrencies" to listOf("EUR"))).getAggregatorGames() }
    }

    test("catalogue fields map onto the variant the engine stores") {
        val games = adapter(mapOf("catalogCurrencies" to listOf("UAH"))).getAggregatorGames()
        val tiger = games.first { it.symbol == "black:pg:1024" }

        tiger.name shouldBe "Fortune Tiger"
        tiger.providerName shouldBe "PG Soft"
        tiger.tags shouldContainExactly listOf("Slot")
        tiger.platforms shouldContainExactly listOf(Platform.DESKTOP, Platform.MOBILE)
        tiger.demoEnable shouldBe true
        tiger.freeSpinEnable shouldBe false
    }

    test("a row with no provider name falls back to the slug inside the game id") {
        val games = adapter(mapOf("catalogCurrencies" to listOf("USD"))).getAggregatorGames()

        games.first { it.symbol == "black:sg:389" }.providerName shouldBe "sg"
    }

    test("a launch sends real-money parameters and returns the provider session id") {
        val session = TestFixtures.session(currency = "UAH", playerId = "player_1", locale = "en")

        val launch = adapter().getLaunchUrl(session = session, lobbyUrl = "")

        launch shouldBe ICasinoGamePort.Launch(
            url = "https://cdn01.example/?session=69581232_abc",
            externalToken = "69581232",
        )
        openGameField("demo") shouldBe "0"
        openGameField("currency") shouldBe "UAH"
        openGameField("player_login") shouldBe "player_1"
        openGameField("user_id") shouldBe userId
        openGameField("gameId") shouldBe session.gameVariant.symbol.value
        openGameField("callbackUrl") shouldBe "https://webhook.prematch.win/api/webhook/gambutsoft"
        // The launch command carries no lobby url, so the configured exit url is what real play uses.
        openGameField("exitUrl") shouldBe "https://prematch.win/casino"
    }

    test("a blank callback url is omitted rather than sent as null") {
        adapter(mapOf("callbackUrl" to "")).getLaunchUrl(
            session = TestFixtures.session(currency = "UAH"),
            lobbyUrl = "",
        )

        openGameRequests.first().containsKey("callbackUrl") shouldBe false
    }

    test("a demo launch runs under the demo login and never opens a wallet session") {
        val url = adapter().getDemoUrl(
            gameSymbol = "black:pg:1024",
            locale = Locale("en"),
            platform = Platform.DESKTOP,
            currency = Currency("UAH"),
            lobbyUrl = "https://prematch.win/games",
        )

        url shouldBe "https://cdn01.example/?session=69581232_abc"
        openGameField("demo") shouldBe "1"
        openGameField("player_login") shouldBe "guest_demo_player"
        openGameField("exitUrl") shouldBe "https://prematch.win/games"
    }

    test("a signature the vendor cannot reproduce is refused") {
        shouldThrowAny {
            GambutsoftGameAdapter(config(mapOf("secretKey" to "wrong"))).getLaunchUrl(
                session = TestFixtures.session(currency = "UAH"),
                lobbyUrl = "",
            )
        }
    }
})
