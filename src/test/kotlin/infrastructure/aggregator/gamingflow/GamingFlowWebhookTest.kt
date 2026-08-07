package infrastructure.aggregator.gamingflow

import application.Bus
import application.command.session.EndRoundSessionCommand
import application.command.session.PlaceSpinSessionCommand
import application.command.session.RollbackSpinSessionCommand
import application.command.session.SettleSpinSessionCommand
import application.port.external.ICurrencyPort
import application.port.external.IWebhookGuardPort
import application.query.aggregator.FindAggregatorQuery
import application.query.session.FindSessionBalanceQuery
import application.query.session.FindSessionQuery
import domain.exception.forbidden.InsufficientBalanceException
import domain.exception.notfound.SessionNotFoundException
import domain.model.PlayerBalance
import domain.vo.Amount
import domain.vo.Currency
import infrastructure.aggregator.gamingflow.webhook.GamingFlowWebhook
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import support.TestFixtures
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Exercises the Seamless API v2 surface the way the provider does: signed JSON-RPC over HTTP.
 *
 * Signatures are built here from the documented algorithm rather than through the production signer,
 * so the two have to agree independently.
 */
class GamingFlowWebhookTest : FunSpec({

    val keyId = "secret-key"
    val keyValue = "s3cr3t"
    val subject = "casino:2239"

    val session = TestFixtures.session(currency = "UAH", playerId = "player_1", token = "token_abc")
    val balance = PlayerBalance(Amount(4_400), Amount.ZERO, Currency("UAH"))

    lateinit var bus: Bus
    lateinit var guard: IWebhookGuardPort
    lateinit var webhook: GamingFlowWebhook

    beforeTest {
        bus = mockk(relaxed = false)
        guard = mockk(relaxed = true)

        val currency = mockk<ICurrencyPort>()
        // System units == minor units here, so amounts round-trip and assertions stay readable.
        coEvery { currency.convertToUnits(any(), any()) } answers { (firstArg<Double>() * 100).toLong() }
        coEvery { currency.convertFromUnits(any(), any()) } answers { firstArg<Long>() / 100.0 }

        coEvery { guard.claimNonce(any(), any()) } returns true
        coEvery { guard.isRolledBack(any()) } returns false

        coEvery { bus(ofType<FindAggregatorQuery>()) } returns java.util.Optional.of(
            TestFixtures.aggregator(
                identity = GamingFlowWebhook.AGGREGATOR_IDENTITY,
                integration = GamingFlowAdapterProvider.INTEGRATION,
                config = mapOf(
                    "apiUrl" to "https://customer.example/v1/signed/",
                    "casinoId" to "2239",
                    "keyId" to keyId,
                    "keyValue" to keyValue,
                )
            )
        )
        coEvery { bus(ofType<FindSessionQuery>()) } returns session
        coEvery { bus(ofType<FindSessionBalanceQuery>()) } returns balance

        webhook = GamingFlowWebhook(bus = bus, currencyPort = currency, guardPort = guard)
    }

    suspend fun io.ktor.client.HttpClient.call(
        method: String,
        params: String,
        nonce: String = "10080051040501637901",
        timestamp: Long = System.currentTimeMillis() / 1000,
        subjectHeader: String = subject,
        tamper: Boolean = false,
    ): HttpResponse {
        val body = """{"jsonrpc":"2.0","method":"$method","id":42,"params":$params}"""
        val signed = if (tamper) "$body " else body

        return post("/api/webhook/gamingflow") {
            header("Content-Type", "application/json")
            header("X-Nonce", nonce)
            header("X-Timestamp", timestamp.toString())
            header("X-Subject", subjectHeader)
            header("X-Signature", "$keyId=" + hmacHex(keyValue, signed, nonce, timestamp))
            setBody(body)
        }
    }

    fun runWebhook(block: suspend (io.ktor.client.HttpClient) -> Unit) = testApplication {
        application {
            routing {
                route("/api/webhook") {
                    with(webhook) { route() }
                }
            }
        }
        block(client)
    }

    test("a tampered body is rejected before anything happens") {
        runWebhook { client ->
            val response = client.call(
                method = "getBalance",
                params = """{"playerName":"player_1_UAH","currency":"UAH","sessionAlternativeId":"token_abc"}""",
                tamper = true,
            )

            response.status shouldBe HttpStatusCode.Unauthorized
            coVerify(exactly = 0) { bus(ofType<FindSessionBalanceQuery>()) }
        }
    }

    test("a wrong subject is rejected") {
        runWebhook { client ->
            val response = client.call("getBalance", """{"sessionAlternativeId":"token_abc"}""", subjectHeader = "casino:9999")

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("a stale timestamp is rejected") {
        runWebhook { client ->
            val response = client.call(
                method = "getBalance",
                params = """{"sessionAlternativeId":"token_abc"}""",
                timestamp = System.currentTimeMillis() / 1000 - 3_600,
            )

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("a replayed nonce is rejected even though the signature is valid") {
        coEvery { guard.claimNonce(any(), any()) } returns false

        runWebhook { client ->
            val response = client.call("getBalance", """{"sessionAlternativeId":"token_abc","currency":"UAH"}""")

            response.status shouldBe HttpStatusCode.Unauthorized
            coVerify(exactly = 0) { bus(ofType<FindSessionBalanceQuery>()) }
        }
    }

    test("getBalance answers the balance in minor units and signs the response") {
        runWebhook { client ->
            val nonce = "10080051040501637901"
            val timestamp = System.currentTimeMillis() / 1000

            val response = client.call(
                method = "getBalance",
                params = """{"callerId":2239,"playerName":"player_1_UAH","currency":"UAH","sessionAlternativeId":"token_abc"}""",
                nonce = nonce,
                timestamp = timestamp,
            )

            response.status shouldBe HttpStatusCode.OK

            val body = response.bodyAsText()
            val result = Json.parseToJsonElement(body).jsonObject["result"]!!.jsonObject
            result["balance"]!!.jsonPrimitive.long shouldBe 4_400L

            // Answered with the request's own nonce/timestamp, and signed over the exact response body.
            response.headers["X-Nonce"] shouldBe nonce
            response.headers["X-Timestamp"] shouldBe timestamp.toString()
            response.headers["X-Subject"] shouldBe subject
            response.headers["X-Signature"] shouldBe "$keyId=" + hmacHex(keyValue, body, nonce, timestamp)
        }
    }

    test("withdrawAndDeposit books a bet and a win from one transaction ref") {
        var place: PlaceSpinSessionCommand? = null
        var settle: SettleSpinSessionCommand? = null
        var endRound: EndRoundSessionCommand? = null

        coEvery { bus(ofType<PlaceSpinSessionCommand>()) } answers { place = firstArg(); balance }
        coEvery { bus(ofType<SettleSpinSessionCommand>()) } answers { settle = firstArg(); balance }
        coEvery { bus(ofType<EndRoundSessionCommand>()) } answers { endRound = firstArg(); Unit }

        runWebhook { client ->
            val response = client.call(
                method = "withdrawAndDeposit",
                params = """
                    {"callerId":2239,"playerName":"player_1_UAH","withdraw":20,"deposit":100,
                     "currency":"UAH","transactionRef":"1:P0GKaKsJEqMzKnEk","gameRoundRef":"1faaf:1kq",
                     "gameId":"magic_maze_html","reason":"GAME_PLAY_FINAL",
                     "sessionAlternativeId":"token_abc"}
                """.trimIndent().replace("\n", ""),
            )

            val result = Json.parseToJsonElement(response.bodyAsText()).jsonObject["result"]!!.jsonObject
            result["newBalance"]!!.jsonPrimitive.long shouldBe 4_400L
            result["transactionId"]!!.jsonPrimitive.content shouldBe "1:P0GKaKsJEqMzKnEk"

            // Both legs hang off the same round and carry ids derived from the transaction ref, which
            // is what makes a redelivery land on the existing spins instead of new ones.
            place!!.externalRoundId shouldBe "1faaf:1kq"
            place!!.externalSpinId shouldBe "1:P0GKaKsJEqMzKnEk:place"
            place!!.amount shouldBe Amount(20)
            place!!.gameSymbol shouldBe "magic_maze_html"

            settle!!.externalRoundId shouldBe "1faaf:1kq"
            settle!!.externalSpinId shouldBe "1:P0GKaKsJEqMzKnEk:settle"
            settle!!.amount shouldBe Amount(100)

            endRound!!.externalRoundId shouldBe "1faaf:1kq"
        }
    }

    test("withdrawAndDeposit keeps the round open unless told to close it") {
        coEvery { bus(ofType<PlaceSpinSessionCommand>()) } returns balance

        runWebhook { client ->
            client.call(
                method = "withdrawAndDeposit",
                params = """{"withdraw":20,"deposit":0,"currency":"UAH","transactionRef":"ref-2","gameRoundRef":"r2","reason":"GAME_PLAY","sessionAlternativeId":"token_abc"}""",
            )

            coVerify(exactly = 0) { bus(ofType<EndRoundSessionCommand>()) }
        }
    }

    test("a transaction already rolled back is refused with code 8") {
        coEvery { guard.isRolledBack(any()) } returns true

        runWebhook { client ->
            val response = client.call(
                method = "withdrawAndDeposit",
                params = """{"withdraw":20,"deposit":0,"currency":"UAH","transactionRef":"ref-3","sessionAlternativeId":"token_abc"}""",
            )

            val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
            error["code"]!!.jsonPrimitive.int shouldBe 8
            coVerify(exactly = 0) { bus(ofType<PlaceSpinSessionCommand>()) }
        }
    }

    test("charging a free round is refused rather than billed as a bet") {
        runWebhook { client ->
            val response = client.call(
                method = "withdrawAndDeposit",
                params = """{"withdraw":0,"deposit":0,"currency":"UAH","transactionRef":"ref-4","chargeFreerounds":1,"bonusId":"b1","sessionAlternativeId":"token_abc"}""",
            )

            val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
            error["code"]!!.jsonPrimitive.int shouldBe 8
            coVerify(exactly = 0) { bus(ofType<PlaceSpinSessionCommand>()) }
        }
    }

    test("rollbackTransaction marks the ref and reverses the win before the bet") {
        var rollback: RollbackSpinSessionCommand? = null
        coEvery { bus(ofType<RollbackSpinSessionCommand>()) } answers { rollback = firstArg(); balance }

        runWebhook { client ->
            val response = client.call(
                method = "rollbackTransaction",
                params = """{"playerName":"player_1_UAH","transactionRef":"1:P0GKaKsJEqMzKnEk","sessionAlternativeId":"token_abc"}""",
            )

            // The provider requires an empty object, not a null result.
            Json.parseToJsonElement(response.bodyAsText()).jsonObject["result"] shouldBe JsonObject(emptyMap())

            rollback!!.externalSpinIds shouldBe listOf(
                "1:P0GKaKsJEqMzKnEk:settle",
                "1:P0GKaKsJEqMzKnEk:place",
            )

            coVerify { guard.markRolledBack(match { it.endsWith("1:P0GKaKsJEqMzKnEk") }, any()) }
        }
    }

    test("insufficient balance answers code 1 and starts no rollback cycle") {
        coEvery { bus(ofType<PlaceSpinSessionCommand>()) } throws InsufficientBalanceException()

        runWebhook { client ->
            val response = client.call(
                method = "withdrawAndDeposit",
                params = """{"withdraw":999999,"deposit":0,"currency":"UAH","transactionRef":"ref-5","sessionAlternativeId":"token_abc"}""",
            )

            val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
            error["code"]!!.jsonPrimitive.int shouldBe 1
        }
    }

    test("an unknown session answers code 7 so no rollback is chased") {
        coEvery { bus(ofType<FindSessionQuery>()) } throws SessionNotFoundException()

        runWebhook { client ->
            val response = client.call(
                method = "getBalance",
                params = """{"currency":"UAH","sessionAlternativeId":"gone"}""",
            )

            val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
            error["code"]!!.jsonPrimitive.int shouldBe 7
        }
    }

    test("a currency the session is not held in answers code 2") {
        runWebhook { client ->
            val response = client.call(
                method = "getBalance",
                params = """{"currency":"EUR","sessionAlternativeId":"token_abc"}""",
            )

            val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
            error["code"]!!.jsonPrimitive.int shouldBe 2
        }
    }

    test("collectBonusReward reports itself unimplemented instead of pretending") {
        runWebhook { client ->
            val response = client.call(
                method = "collectBonusReward",
                params = """{"depositAmount":100,"currency":"UAH","transactionRef":"ref-6","bonusId":"b1","sessionAlternativeId":"token_abc"}""",
            )

            val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
            error["code"]!!.jsonPrimitive.int shouldBe -32601
        }
    }

    test("the response id always echoes the request id") {
        runWebhook { client ->
            val response = client.call("getBalance", """{"currency":"UAH","sessionAlternativeId":"token_abc"}""")

            Json.parseToJsonElement(response.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.int shouldBe 42
            response.headers["X-Signature"].shouldNotBeNull()
        }
    }
})

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
