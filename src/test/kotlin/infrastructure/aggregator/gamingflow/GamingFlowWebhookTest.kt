package infrastructure.aggregator.gamingflow

import application.Bus
import application.command.freespin.ChargeFreespinCommand
import application.command.session.EndCasinoRoundSessionCommand
import application.command.session.PlaceSpinCasinoSessionCommand
import application.command.session.RollbackSpinCasinoSessionCommand
import application.command.session.SettleSpinCasinoSessionCommand
import application.port.external.ICurrencyPort
import application.port.external.IWebhookGuardPort
import application.query.aggregator.FindAggregatorQuery
import application.query.freespin.FindRedeemableFreespinQuery
import application.query.session.FindCasinoSessionBalanceQuery
import application.query.session.FindCasinoSessionByExternalTokenQuery
import application.query.session.FindCasinoSessionQuery
import domain.exception.conflict.FreespinExhaustedException
import domain.exception.forbidden.InsufficientBalanceException
import domain.exception.notfound.CasinoSessionNotFoundException
import domain.model.Freespin
import domain.model.PlayerBalance
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.FreespinId
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
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
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

    fun freespin(remaining: Int) = Freespin(
        referenceId = FreespinId("bonus-1"),
        playerId = session.playerId,
        gameVariant = session.gameVariant,
        currency = session.currency,
        spinAmount = Amount(25),
        totalCount = 10,
        remainingCount = remaining,
        startAt = Instant.parse("2026-08-13T00:00:00Z"),
        endAt = Instant.parse("2026-09-13T00:00:00Z"),
    )

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
        // Uncontended: run the guarded body inline so these specs exercise the money path itself.
        coEvery { guard.withLock<Any>(any(), any()) } coAnswers { secondArg<suspend () -> Any>().invoke() }

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
        coEvery { bus(ofType<FindCasinoSessionQuery>()) } returns session
        coEvery { bus(ofType<FindCasinoSessionBalanceQuery>()) } returns balance
        // No grant unless a spec says otherwise: most spins are not free.
        coEvery { bus(ofType<FindRedeemableFreespinQuery>()) } returns null

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
            coVerify(exactly = 0) { bus(ofType<FindCasinoSessionBalanceQuery>()) }
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
            coVerify(exactly = 0) { bus(ofType<FindCasinoSessionBalanceQuery>()) }
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
        var place: PlaceSpinCasinoSessionCommand? = null
        var settle: SettleSpinCasinoSessionCommand? = null
        var endRound: EndCasinoRoundSessionCommand? = null

        coEvery { bus(ofType<PlaceSpinCasinoSessionCommand>()) } answers { place = firstArg(); balance }
        coEvery { bus(ofType<SettleSpinCasinoSessionCommand>()) } answers { settle = firstArg(); balance }
        coEvery { bus(ofType<EndCasinoRoundSessionCommand>()) } answers { endRound = firstArg(); Unit }

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
        coEvery { bus(ofType<PlaceSpinCasinoSessionCommand>()) } returns balance

        runWebhook { client ->
            client.call(
                method = "withdrawAndDeposit",
                params = """{"withdraw":20,"deposit":0,"currency":"UAH","transactionRef":"ref-2","gameRoundRef":"r2","reason":"GAME_PLAY","sessionAlternativeId":"token_abc"}""",
            )

            coVerify(exactly = 0) { bus(ofType<EndCasinoRoundSessionCommand>()) }
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
            coVerify(exactly = 0) { bus(ofType<PlaceSpinCasinoSessionCommand>()) }
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
            coVerify(exactly = 0) { bus(ofType<PlaceSpinCasinoSessionCommand>()) }
        }
    }

    test("rollbackTransaction marks the ref and reverses the win before the bet") {
        var rollback: RollbackSpinCasinoSessionCommand? = null
        coEvery { bus(ofType<RollbackSpinCasinoSessionCommand>()) } answers { rollback = firstArg(); balance }

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
        coEvery { bus(ofType<PlaceSpinCasinoSessionCommand>()) } throws InsufficientBalanceException()

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
        coEvery { bus(ofType<FindCasinoSessionQuery>()) } throws CasinoSessionNotFoundException()

        runWebhook { client ->
            val response = client.call(
                method = "getBalance",
                params = """{"currency":"UAH","sessionAlternativeId":"gone"}""",
            )

            val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
            error["code"]!!.jsonPrimitive.int shouldBe 7
        }
    }

    test("getBalance reports the free rounds we still hold for the player") {
        coEvery { bus(ofType<FindRedeemableFreespinQuery>()) } returns freespin(remaining = 7)

        runWebhook { client ->
            val response = client.call(
                method = "getBalance",
                params = """{"currency":"UAH","sessionAlternativeId":"token_abc"}""",
            )

            val result = Json.parseToJsonElement(response.bodyAsText()).jsonObject["result"]!!.jsonObject
            result["freeroundsLeft"]!!.jsonPrimitive.int shouldBe 7
        }
    }

    test("a charged free round replaces the bet and still pays the win") {
        coEvery { bus(ofType<ChargeFreespinCommand>()) } returns freespin(remaining = 4)
        coEvery { bus(ofType<SettleSpinCasinoSessionCommand>()) } returns balance

        runWebhook { client ->
            val response = client.call(
                method = "withdrawAndDeposit",
                params = """{"withdraw":25,"deposit":100,"currency":"UAH","transactionRef":"ref-fs",
                     "gameRoundRef":"r-fs","bonusId":"bonus-1","chargeFreerounds":1,
                     "sessionAlternativeId":"token_abc"}""",
            )

            val result = Json.parseToJsonElement(response.bodyAsText()).jsonObject["result"]!!.jsonObject
            result["freeroundsLeft"]!!.jsonPrimitive.int shouldBe 4
            result["freeroundWasCharged"]!!.jsonPrimitive.boolean shouldBe true
        }

        // The stake is not taken — that is what "free" means — but the win is credited as usual,
        // and both spins carry the grant so the round is identifiable as a bonus round.
        coVerify(exactly = 0) { bus(ofType<PlaceSpinCasinoSessionCommand>()) }
        coVerify(exactly = 1) { bus(match<SettleSpinCasinoSessionCommand> { it.freespinId == "bonus-1" }) }
    }

    test("a free round the grant cannot cover is refused, not silently billed") {
        coEvery { bus(ofType<ChargeFreespinCommand>()) } throws FreespinExhaustedException()

        runWebhook { client ->
            val response = client.call(
                method = "withdrawAndDeposit",
                params = """{"withdraw":25,"deposit":0,"currency":"UAH","transactionRef":"ref-fs2",
                     "bonusId":"bonus-1","chargeFreerounds":1,"sessionAlternativeId":"token_abc"}""",
            )

            val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
            error["code"]!!.jsonPrimitive.int shouldBe 8
        }

        coVerify(exactly = 0) { bus(ofType<PlaceSpinCasinoSessionCommand>()) }
    }

    test("chargeFreerounds without a bonus id is an ordinary paid bet") {
        coEvery { bus(ofType<PlaceSpinCasinoSessionCommand>()) } returns balance

        runWebhook { client ->
            client.call(
                method = "withdrawAndDeposit",
                params = """{"withdraw":25,"deposit":0,"currency":"UAH","transactionRef":"ref-paid",
                     "chargeFreerounds":1,"sessionAlternativeId":"token_abc"}""",
            )
        }

        coVerify(exactly = 0) { bus(ofType<ChargeFreespinCommand>()) }
        coVerify(exactly = 1) { bus(ofType<PlaceSpinCasinoSessionCommand>()) }
    }

    test("a money call is serialised on the player's wallet, a balance read is not") {
        runWebhook { client ->
            client.call(
                method = "withdrawAndDeposit",
                params = """{"withdraw":14,"deposit":20,"currency":"UAH","transactionRef":"ref-lock",
                     "gameRoundRef":"r-lock","sessionAlternativeId":"token_abc"}""",
            )
            client.call("getBalance", """{"currency":"UAH","sessionAlternativeId":"token_abc"}""")
        }

        // Keyed on the wallet account: two games of the same player share one balance, two players
        // must never wait on each other.
        coVerify(exactly = 1) { guard.withLock<Any>("gamingflow:wallet:player_1:UAH", any()) }
    }

    test("a session is resolved by the provider's own id when the alternative id is not ours") {
        // What the vendor's integration-test harness actually sends: the game's section id in
        // sessionAlternativeId, the real identifier in sessionId.
        coEvery { bus(ofType<FindCasinoSessionQuery>()) } throws CasinoSessionNotFoundException()
        coEvery { bus(ofType<FindCasinoSessionByExternalTokenQuery>()) } returns session

        runWebhook { client ->
            val response = client.call(
                method = "getBalance",
                params = """{"currency":"UAH","sessionId":"q31d0lghlxf67ep","sessionAlternativeId":"bgaming"}""",
            )

            val result = Json.parseToJsonElement(response.bodyAsText()).jsonObject["result"]!!.jsonObject
            result["balance"]!!.jsonPrimitive.long shouldBe 4_400
        }

        coVerify(exactly = 1) { bus(FindCasinoSessionByExternalTokenQuery("q31d0lghlxf67ep")) }
    }

    test("our own alternative id wins over the provider's id when both resolve") {
        runWebhook { client ->
            client.call(
                method = "getBalance",
                params = """{"currency":"UAH","sessionId":"q31d0lghlxf67ep","sessionAlternativeId":"token_abc"}""",
            )
        }

        coVerify(exactly = 0) { bus(ofType<FindCasinoSessionByExternalTokenQuery>()) }
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
