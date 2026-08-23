package infrastructure.aggregator.gambutsoft

import application.Bus
import application.command.session.EndCasinoRoundSessionCommand
import application.command.session.PlaceSpinCasinoSessionCommand
import application.command.session.RollbackSpinCasinoSessionCommand
import application.command.session.SettleSpinCasinoSessionCommand
import application.port.external.IWebhookGuardPort
import application.query.aggregator.FindAggregatorQuery
import application.query.session.FindCasinoSessionBalanceQuery
import application.query.session.FindCasinoSessionByExternalTokenQuery
import domain.exception.conflict.CasinoRoundAlreadyFinishedException
import domain.exception.forbidden.InsufficientBalanceException
import domain.exception.notfound.CasinoSessionNotFoundException
import domain.model.PlayerBalance
import domain.vo.Amount
import domain.vo.Currency
import infrastructure.aggregator.gambutsoft.webhook.GambutsoftWebhook
import io.kotest.core.spec.style.FunSpec
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
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import support.TestFixtures
import java.util.Optional
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Exercises the wallet callbacks the way the vendor sends them: one signed POST, the command in the
 * body. Signatures are built here from the documented algorithm rather than through the production
 * signer, so the two have to agree independently.
 *
 * Amounts on the wire are major units while the wallet works in nano, so every money assertion is
 * really a conversion assertion: 25 in, 25_000_000_000 through the bus, 2500 back out.
 */
class GambutsoftWebhookTest : FunSpec({

    val secretKey = "s3cr3t"

    val aggregator = TestFixtures.aggregator(
        identity = GambutsoftWebhook.AGGREGATOR_IDENTITY,
        integration = GambutsoftAdapterProvider.INTEGRATION,
        config = mapOf("secretKey" to secretKey),
    )

    val session = TestFixtures.session(
        variant = TestFixtures.gameVariant(
            game = TestFixtures.game(provider = TestFixtures.provider(aggregator = aggregator)),
        ),
        currency = "UAH",
        playerId = "player_1",
        token = "token_abc",
    )

    val balance = PlayerBalance(Amount(2_500_000_000_000), Amount.ZERO, Currency("UAH"))

    lateinit var bus: Bus
    lateinit var guard: IWebhookGuardPort
    lateinit var webhook: GambutsoftWebhook

    fun hmacHex(body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey.toByteArray(), "HmacSHA256"))
        return mac.doFinal(body.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    beforeTest {
        bus = mockk(relaxed = false)
        guard = mockk(relaxed = true)

        coEvery { guard.isRolledBack(any()) } returns false
        // Uncontended: run the guarded body inline so these specs exercise the money path itself.
        coEvery { guard.withLock<Any>(any(), any()) } coAnswers { secondArg<suspend () -> Any>().invoke() }

        coEvery { bus(ofType<FindAggregatorQuery>()) } returns Optional.of(aggregator)
        coEvery { bus(ofType<FindCasinoSessionByExternalTokenQuery>()) } returns session
        coEvery { bus(ofType<FindCasinoSessionBalanceQuery>()) } returns balance
        coEvery { bus(ofType<PlaceSpinCasinoSessionCommand>()) } returns balance
        coEvery { bus(ofType<SettleSpinCasinoSessionCommand>()) } returns balance
        coEvery { bus(ofType<RollbackSpinCasinoSessionCommand>()) } returns balance
        coEvery { bus(ofType<EndCasinoRoundSessionCommand>()) } returns Unit

        webhook = GambutsoftWebhook(bus = bus, guardPort = guard)
    }

    suspend fun io.ktor.client.HttpClient.callback(body: String, signature: String = hmacHex(body)): HttpResponse =
        post("/api/webhook/gambutsoft") {
            header("Content-Type", "application/json")
            header("X-Signature", signature)
            setBody(body)
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

    suspend fun HttpResponse.field(name: String): String =
        Json.parseToJsonElement(bodyAsText()).jsonObject.getValue(name).jsonPrimitive.content

    test("a body signed with the wrong key never reaches the session") {
        runWebhook { client ->
            val response = client.callback(
                body = """{"cmd":"getBalance","login":"player_1","sessionid":"69581232"}""",
                signature = hmacHex("tampered"),
            )

            response.status shouldBe HttpStatusCode.BadRequest
            response.field("status") shouldBe "fail"
            response.field("error") shouldBe "invalid signature"
            coVerify(exactly = 0) { bus(ofType<FindCasinoSessionByExternalTokenQuery>()) }
        }
    }

    test("getBalance answers the wallet balance in major units") {
        runWebhook { client ->
            val response = client.callback("""{"cmd":"getBalance","login":"player_1","sessionid":"69581232"}""")

            response.status shouldBe HttpStatusCode.OK
            response.field("status") shouldBe "success"
            response.field("balance") shouldBe "2500"
            response.field("currency") shouldBe "UAH"
            response.field("login") shouldBe "player_1"
            coVerify(exactly = 1) { bus(FindCasinoSessionByExternalTokenQuery("69581232")) }
        }
    }

    test("an unknown session is refused without disclosing a balance") {
        coEvery { bus(ofType<FindCasinoSessionByExternalTokenQuery>()) } throws CasinoSessionNotFoundException()

        runWebhook { client ->
            val response = client.callback("""{"cmd":"getBalance","login":"player_1","sessionid":"nope"}""")

            response.status shouldBe HttpStatusCode.BadRequest
            response.field("status") shouldBe "fail"
            response.field("error") shouldBe "session not found"
            response.field("balance") shouldBe "0"
        }
    }

    test("a bet becomes one PLACE spin in nano units") {
        var place: PlaceSpinCasinoSessionCommand? = null
        coEvery { bus(ofType<PlaceSpinCasinoSessionCommand>()) } answers { place = firstArg(); balance }

        runWebhook { client ->
            val response = client.callback(
                """{"cmd":"writeBet","bet":25,"win":0,"login":"player_1","sessionid":"69581232",""" +
                    """"transactionId":"txn_1","round_finished":false,"info":"{}"}"""
            )

            response.status shouldBe HttpStatusCode.OK
            response.field("status") shouldBe "success"
            place?.amount shouldBe Amount(25_000_000_000)
            place?.externalSpinId shouldBe "txn_1:place"
            // No roundId on the wire: the transaction is its own round.
            place?.externalRoundId shouldBe "txn_1"
            coVerify(exactly = 0) { bus(ofType<SettleSpinCasinoSessionCommand>()) }
        }
    }

    test("a bet sent as a string is the same money") {
        var place: PlaceSpinCasinoSessionCommand? = null
        coEvery { bus(ofType<PlaceSpinCasinoSessionCommand>()) } answers { place = firstArg(); balance }

        runWebhook { client ->
            client.callback(
                """{"cmd":"writeBet","bet":"25.50","win":"0","login":"player_1","sessionid":"69581232",""" +
                    """"transactionId":"txn_2","round_finished":false,"info":"{}"}"""
            )

            place?.amount shouldBe Amount(25_500_000_000)
        }
    }

    test("bet and win in one callback are two spins, bet first") {
        runWebhook { client ->
            val response = client.callback(
                """{"cmd":"writeBet","bet":25,"win":75,"login":"player_1","sessionid":"69581232",""" +
                    """"transactionId":"txn_3","roundId":"r_9f4a","round_finished":true,"info":"{}"}"""
            )

            response.status shouldBe HttpStatusCode.OK
            coVerifyOrder {
                bus(match<PlaceSpinCasinoSessionCommand> { it.amount == Amount(25_000_000_000) && it.externalRoundId == "r_9f4a" })
                bus(match<SettleSpinCasinoSessionCommand> { it.amount == Amount(75_000_000_000) && it.externalSpinId == "txn_3:settle" })
                bus(EndCasinoRoundSessionCommand(session = session, externalRoundId = "r_9f4a"))
            }
        }
    }

    test("a round is only closed when the vendor says it finished") {
        runWebhook { client ->
            client.callback(
                """{"cmd":"writeBet","bet":25,"win":0,"login":"player_1","sessionid":"69581232",""" +
                    """"transactionId":"txn_4","round_finished":false,"info":"{}"}"""
            )

            coVerify(exactly = 0) { bus(ofType<EndCasinoRoundSessionCommand>()) }
        }
    }

    test("a round that cannot be closed does not fail a callback whose money already moved") {
        coEvery { bus(ofType<EndCasinoRoundSessionCommand>()) } throws IllegalStateException("round gone")

        runWebhook { client ->
            val response = client.callback(
                """{"cmd":"writeBet","bet":0,"win":75,"login":"player_1","sessionid":"69581232",""" +
                    """"transactionId":"txn_5","round_finished":true,"info":"{}"}"""
            )

            response.status shouldBe HttpStatusCode.OK
            response.field("status") shouldBe "success"
        }
    }

    test("an unaffordable bet is declined with the real balance, not a zero") {
        coEvery { bus(ofType<PlaceSpinCasinoSessionCommand>()) } throws InsufficientBalanceException()

        runWebhook { client ->
            val response = client.callback(
                """{"cmd":"writeBet","bet":25,"win":0,"login":"player_1","sessionid":"69581232",""" +
                    """"transactionId":"txn_6","round_finished":false,"info":"{}"}"""
            )

            response.status shouldBe HttpStatusCode.BadRequest
            response.field("status") shouldBe "fail"
            response.field("error") shouldBe "insufficient funds"
            response.field("balance") shouldBe "2500"
            coVerify(exactly = 0) { bus(ofType<SettleSpinCasinoSessionCommand>()) }
        }
    }

    test("a negative amount is refused before it reaches the wallet") {
        runWebhook { client ->
            val response = client.callback(
                """{"cmd":"writeBet","bet":-25,"win":0,"login":"player_1","sessionid":"69581232",""" +
                    """"transactionId":"txn_7","round_finished":false,"info":"{}"}"""
            )

            response.status shouldBe HttpStatusCode.BadRequest
            response.field("status") shouldBe "fail"
            coVerify(exactly = 0) { bus(ofType<PlaceSpinCasinoSessionCommand>()) }
        }
    }

    test("a transaction that was already rolled back is refused instead of executed") {
        coEvery { guard.isRolledBack("gambutsoft:txn_8") } returns true

        runWebhook { client ->
            val response = client.callback(
                """{"cmd":"writeBet","bet":25,"win":0,"login":"player_1","sessionid":"69581232",""" +
                    """"transactionId":"txn_8","round_finished":false,"info":"{}"}"""
            )

            response.status shouldBe HttpStatusCode.BadRequest
            response.field("error") shouldBe "transaction already rolled back"
            coVerify(exactly = 0) { bus(ofType<PlaceSpinCasinoSessionCommand>()) }
        }
    }

    test("a rollback marks the transaction before reversing win then bet") {
        runWebhook { client ->
            val response = client.callback(
                """{"cmd":"rollback","bet":25,"win":0,"login":"player_1","sessionid":"69581232",""" +
                    """"transactionId":"txn_9","round_finished":true,"info":"{}","gameId":"black:pragmatic:1005"}"""
            )

            response.status shouldBe HttpStatusCode.OK
            response.field("status") shouldBe "success"
            response.field("balance") shouldBe "2500"
            coVerifyOrder {
                guard.markRolledBack("gambutsoft:txn_9", any())
                bus(
                    RollbackSpinCasinoSessionCommand(
                        session = session,
                        externalSpinIds = listOf("txn_9:settle", "txn_9:place"),
                    )
                )
            }
        }
    }

    test("money calls are serialised on the player wallet") {
        runWebhook { client ->
            client.callback(
                """{"cmd":"writeBet","bet":25,"win":0,"login":"player_1","sessionid":"69581232",""" +
                    """"transactionId":"txn_10","round_finished":false,"info":"{}"}"""
            )

            coVerify(exactly = 1) { guard.withLock<Any>("gambutsoft:wallet:player_1:UAH", any()) }
        }
    }

    test("a session opened on another aggregator is not ours to move money on") {
        coEvery { bus(ofType<FindCasinoSessionByExternalTokenQuery>()) } returns TestFixtures.session(
            currency = "UAH",
            playerId = "player_1",
        )

        runWebhook { client ->
            val response = client.callback(
                """{"cmd":"writeBet","bet":25,"win":0,"login":"player_1","sessionid":"69581232",""" +
                    """"transactionId":"txn_11","round_finished":false,"info":"{}"}"""
            )

            response.status shouldBe HttpStatusCode.BadRequest
            response.field("error") shouldBe "session not found"
            coVerify(exactly = 0) { bus(ofType<PlaceSpinCasinoSessionCommand>()) }
        }
    }

    test("an explicit null amount is read as zero, not as a broken callback") {
        runWebhook { client ->
            val response = client.callback(
                """{"cmd":"writeBet","bet":null,"win":75,"login":"player_1","sessionid":"69581232",""" +
                    """"transactionId":"txn_12","round_finished":false,"info":"{}"}"""
            )

            response.status shouldBe HttpStatusCode.OK
            coVerify(exactly = 0) { bus(ofType<PlaceSpinCasinoSessionCommand>()) }
            coVerify(exactly = 1) { bus(match<SettleSpinCasinoSessionCommand> { it.amount == Amount(75_000_000_000) }) }
        }
    }

    test("a refusal after the bet leg landed still reports the real balance") {
        coEvery { bus(ofType<SettleSpinCasinoSessionCommand>()) } throws CasinoRoundAlreadyFinishedException()

        runWebhook { client ->
            val response = client.callback(
                """{"cmd":"writeBet","bet":25,"win":75,"login":"player_1","sessionid":"69581232",""" +
                    """"transactionId":"txn_13","round_finished":true,"info":"{}"}"""
            )

            response.status shouldBe HttpStatusCode.BadRequest
            response.field("status") shouldBe "fail"
            response.field("currency") shouldBe "UAH"
            response.field("balance") shouldBe "2500"
        }
    }

    test("a balance read is serialised against the money calls it could interleave with") {
        runWebhook { client ->
            client.callback("""{"cmd":"getBalance","login":"player_1","sessionid":"69581232"}""")

            coVerify(exactly = 1) { guard.withLock<Any>("gambutsoft:wallet:player_1:UAH", any()) }
        }
    }

    test("an unknown command is refused") {
        runWebhook { client ->
            val response = client.callback("""{"cmd":"transfer","login":"player_1","sessionid":"69581232"}""")

            response.status shouldBe HttpStatusCode.BadRequest
            response.field("error") shouldBe "unsupported command"
        }
    }
})
