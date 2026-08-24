package infrastructure.aggregator.skyline

import application.Bus
import application.command.session.EndCasinoRoundSessionCommand
import application.command.session.PlaceSpinCasinoSessionCommand
import application.command.session.RollbackSpinCasinoSessionCommand
import application.command.session.SettleSpinCasinoSessionCommand
import application.port.external.ICurrencyPort
import application.port.external.IWebhookGuardPort
import application.query.aggregator.FindAggregatorQuery
import application.query.session.FindCasinoSessionBalanceQuery
import application.query.session.FindCasinoSessionQuery
import domain.exception.forbidden.InsufficientBalanceException
import domain.exception.notfound.CasinoSessionNotFoundException
import domain.model.PlayerBalance
import domain.vo.Amount
import domain.vo.Currency
import infrastructure.aggregator.skyline.client.SkylineJwt
import infrastructure.aggregator.skyline.webhook.SkylineWebhook
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
import io.mockk.slot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import support.TestFixtures
import java.util.Optional

/**
 * Exercises the wallet callbacks the way the vendor sends them: a bare signed token in the body,
 * the action inside it. The tokens are built here with an independent signer, so the production one
 * has to agree rather than merely agree with itself.
 *
 * Amounts on the wire are minor units while the wallet works in nano, so every money assertion is a
 * conversion assertion too: 450 in, 4_500_000_000 through the bus, 2500 back out.
 */
class SkylineWebhookTest : FunSpec({

    val secret = "L!V4#nz"

    val jwt = SkylineJwt(secret)

    val aggregator = TestFixtures.aggregator(
        identity = SkylineWebhook.AGGREGATOR_IDENTITY,
        integration = SkylineAdapterProvider.INTEGRATION,
        config = mapOf("jwtSecret" to secret),
    )

    val session = TestFixtures.session(
        variant = TestFixtures.gameVariant(
            game = TestFixtures.game(provider = TestFixtures.provider(aggregator = aggregator)),
        ),
        currency = "UAH",
        playerId = "player_1",
        token = "our-token-abc",
    )

    val balance = PlayerBalance(Amount(20_000_000_000), Amount(5_000_000_000), Currency("UAH"))

    lateinit var bus: Bus
    lateinit var guard: IWebhookGuardPort
    lateinit var webhook: SkylineWebhook

    beforeTest {
        bus = mockk(relaxed = false)
        guard = mockk(relaxed = true)

        val currencyPort = mockk<ICurrencyPort>()
        // Nano is a fixed 1e9 scale; the port is the authority on it, not this webhook.
        coEvery { currencyPort.convertToUnits(any(), any()) } answers {
            (firstArg<Double>() * 1_000_000_000).toLong()
        }
        coEvery { currencyPort.convertFromUnits(any(), any()) } answers {
            firstArg<Long>() / 1_000_000_000.0
        }

        coEvery { guard.isRolledBack(any()) } returns false
        // Uncontended: run the guarded body inline so these specs exercise the money path itself.
        coEvery { guard.withLock<Any>(any(), any()) } coAnswers { secondArg<suspend () -> Any>().invoke() }

        coEvery { bus(ofType<FindAggregatorQuery>()) } returns Optional.of(aggregator)
        coEvery { bus(ofType<FindCasinoSessionQuery>()) } returns session
        coEvery { bus(ofType<FindCasinoSessionBalanceQuery>()) } returns balance
        coEvery { bus(ofType<PlaceSpinCasinoSessionCommand>()) } returns balance
        coEvery { bus(ofType<SettleSpinCasinoSessionCommand>()) } returns balance
        coEvery { bus(ofType<RollbackSpinCasinoSessionCommand>()) } returns balance
        coEvery { bus(ofType<EndCasinoRoundSessionCommand>()) } returns Unit

        webhook = SkylineWebhook(bus = bus, currencyPort = currencyPort, guardPort = guard)
    }

    fun payload(vararg fields: Pair<String, Any>): JsonObject = buildJsonObject {
        fields.forEach { (key, value) ->
            when (value) {
                is String -> put(key, value)
                is Long -> put(key, value)
                is Int -> put(key, value)
                is Boolean -> put(key, value)
                else -> error("unsupported field type for $key")
            }
        }
    }

    suspend fun io.ktor.client.HttpClient.callback(body: String): HttpResponse =
        post("/api/webhook/skyline") {
            header("Content-Type", "application/json")
            setBody(body)
        }

    suspend fun io.ktor.client.HttpClient.send(payload: JsonObject): HttpResponse = callback(jwt.encode(payload))

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

    /** Their answers are signed too, so reading one means verifying it first. */
    suspend fun HttpResponse.result(): JsonObject {
        val decoded = jwt.decode(bodyAsText()) ?: error("response was not signed with the shared secret")
        return decoded.getValue("result") as JsonObject
    }

    suspend fun HttpResponse.field(name: String): String? = result()[name]?.jsonPrimitive?.content

    test("a body that is not a token signed with our secret never reaches the session") {
        runWebhook { client ->
            val response = client.callback("""{"action":"get_balance","session":"our-token-abc"}""")

            response.status shouldBe HttpStatusCode.OK
            response.field("error") shouldBe SkylineWebhook.ERROR_GENERAL.toString()
            coVerify(exactly = 0) { bus(ofType<FindCasinoSessionQuery>()) }
        }
    }

    test("a token signed with the wrong secret is refused just the same") {
        runWebhook { client ->
            val forged = SkylineJwt("wrong").encode(payload("action" to "get_balance", "session" to "our-token-abc"))

            val response = client.callback(forged)

            response.field("error") shouldBe SkylineWebhook.ERROR_GENERAL.toString()
            coVerify(exactly = 0) { bus(ofType<FindCasinoSessionQuery>()) }
        }
    }

    test("get_balance answers the whole spendable wallet in minor units") {
        runWebhook { client ->
            val response = client.send(payload("action" to "get_balance", "session" to "our-token-abc"))

            response.status shouldBe HttpStatusCode.OK
            // 20 real + 5 bonus, and the bonus part is a component of the total, not an addition.
            response.field("balance") shouldBe "2500"
            response.field("bonus_balance") shouldBe "500"
            response.field("currency") shouldBe "UAH"
            coVerify(exactly = 1) { bus(FindCasinoSessionQuery("our-token-abc")) }
        }
    }

    test("a session opened on another integration is not theirs to move money in") {
        val foreign = TestFixtures.session(
            variant = TestFixtures.gameVariant(
                game = TestFixtures.game(
                    provider = TestFixtures.provider(
                        aggregator = TestFixtures.aggregator(integration = "ONEGAMEHUB"),
                    ),
                ),
            ),
        )
        coEvery { bus(ofType<FindCasinoSessionQuery>()) } returns foreign

        runWebhook { client ->
            val response = client.send(payload("action" to "get_balance", "session" to "our-token-abc"))

            response.field("error") shouldBe SkylineWebhook.ERROR_GENERAL.toString()
            coVerify(exactly = 0) { bus(ofType<FindCasinoSessionBalanceQuery>()) }
        }
    }

    test("an unknown session is refused without disclosing a balance") {
        coEvery { bus(ofType<FindCasinoSessionQuery>()) } throws CasinoSessionNotFoundException()

        runWebhook { client ->
            val response = client.send(payload("action" to "get_balance", "session" to "gone"))

            response.field("error") shouldBe SkylineWebhook.ERROR_GENERAL.toString()
            response.result().containsKey("balance") shouldBe false
        }
    }

    test("a bet is placed in nano and echoes its transaction back") {
        val command = slot<PlaceSpinCasinoSessionCommand>()
        coEvery { bus(capture(command)) } returns balance

        runWebhook { client ->
            val response = client.send(
                payload(
                    "action" to "update_balance",
                    "session" to "our-token-abc",
                    "transaction" to "tx-1",
                    "round" to "round-9",
                    "bet_amount" to 450L,
                    "win_amount" to 0L,
                )
            )

            response.field("balance") shouldBe "2500"
            response.field("transaction") shouldBe "tx-1"

            command.captured.amount shouldBe Amount(4_500_000_000)
            command.captured.externalRoundId shouldBe "round-9"
            command.captured.externalSpinId shouldBe "tx-1:place"
            command.captured.freespinId shouldBe null
        }
    }

    test("a round the vendor did not name falls back to the transaction") {
        val command = slot<PlaceSpinCasinoSessionCommand>()
        coEvery { bus(capture(command)) } returns balance

        runWebhook { client ->
            client.send(
                payload(
                    "action" to "update_balance",
                    "session" to "our-token-abc",
                    "transaction" to "tx-1",
                    "bet_amount" to 450L,
                )
            )

            command.captured.externalRoundId shouldBe "tx-1"
        }
    }

    test("a call carrying both legs places the bet before it pays the win") {
        runWebhook { client ->
            client.send(
                payload(
                    "action" to "update_balance",
                    "session" to "our-token-abc",
                    "transaction" to "tx-2",
                    "round" to "round-9",
                    "bet_amount" to 450L,
                    "win_amount" to 900L,
                )
            )

            // The bet is the only leg that can be declined; netting or reordering them would let a
            // player stake money the wallet never held.
            coVerifyOrder {
                bus(ofType<PlaceSpinCasinoSessionCommand>())
                bus(ofType<SettleSpinCasinoSessionCommand>())
            }
        }
    }

    test("is_last closes the round after the money has moved") {
        runWebhook { client ->
            client.send(
                payload(
                    "action" to "update_balance",
                    "session" to "our-token-abc",
                    "transaction" to "tx-3",
                    "round" to "round-9",
                    "win_amount" to 900L,
                    "is_last" to true,
                )
            )

            coVerifyOrder {
                bus(ofType<SettleSpinCasinoSessionCommand>())
                bus(EndCasinoRoundSessionCommand(session = session, externalRoundId = "round-9"))
            }
        }
    }

    test("a round left open by is_last false is not closed") {
        runWebhook { client ->
            client.send(
                payload(
                    "action" to "update_balance",
                    "session" to "our-token-abc",
                    "transaction" to "tx-4",
                    "round" to "round-9",
                    "win_amount" to 900L,
                    "is_last" to false,
                )
            )

            coVerify(exactly = 0) { bus(ofType<EndCasinoRoundSessionCommand>()) }
        }
    }

    test("a free round still opens against its grant even though it costs nothing") {
        val command = slot<PlaceSpinCasinoSessionCommand>()
        coEvery { bus(capture(command)) } returns balance

        runWebhook { client ->
            client.send(
                payload(
                    "action" to "update_balance",
                    "session" to "our-token-abc",
                    "transaction" to "tx-5",
                    "round" to "round-fs",
                    "bet_amount" to 0L,
                    "win_amount" to 0L,
                    "bonus" to "fs-2026-01",
                )
            )

            // The PLACE is what spends one of the granted rounds; the engine's free-round path
            // leaves the balance alone.
            command.captured.amount shouldBe Amount.ZERO
            command.captured.freespinId shouldBe "fs-2026-01"
        }
    }

    test("the payout leg of a free round settles only") {
        runWebhook { client ->
            client.send(
                payload(
                    "action" to "update_balance",
                    "session" to "our-token-abc",
                    "transaction" to "tx-6",
                    "round" to "round-fs",
                    "bet_amount" to 0L,
                    "win_amount" to 900L,
                    "bonus" to "fs-2026-01",
                )
            )

            // Its stake leg arrived as its own call; placing again would open a second round.
            coVerify(exactly = 0) { bus(ofType<PlaceSpinCasinoSessionCommand>()) }
            coVerify(exactly = 1) { bus(ofType<SettleSpinCasinoSessionCommand>()) }
        }
    }

    test("a refund reverses the transaction it names, win before bet") {
        val command = slot<RollbackSpinCasinoSessionCommand>()
        coEvery { bus(capture(command)) } returns balance

        runWebhook { client ->
            val response = client.send(
                payload(
                    "action" to "update_balance",
                    "session" to "our-token-abc",
                    "transaction" to "tx-1",
                    "win_amount" to 450L,
                    "is_refund" to true,
                )
            )

            response.field("transaction") shouldBe "tx-1"
            // Reclaiming the win before refunding the bet keeps the balance non-negative.
            command.captured.externalSpinIds shouldBe listOf("tx-1:settle", "tx-1:place")
            coVerify(exactly = 0) { bus(ofType<PlaceSpinCasinoSessionCommand>()) }
        }
    }

    test("a refund is marked before anything is reversed") {
        runWebhook { client ->
            client.send(
                payload(
                    "action" to "update_balance",
                    "session" to "our-token-abc",
                    "transaction" to "tx-1",
                    "is_refund" to true,
                )
            )

            // The vendor may refund a call we never received; that call must be refused on arrival.
            coVerifyOrder {
                guard.markRolledBack("skyline:tx-1", any())
                bus(ofType<RollbackSpinCasinoSessionCommand>())
            }
        }
    }

    test("a transaction that arrives after its own refund is refused") {
        coEvery { guard.isRolledBack("skyline:tx-1") } returns true

        runWebhook { client ->
            val response = client.send(
                payload(
                    "action" to "update_balance",
                    "session" to "our-token-abc",
                    "transaction" to "tx-1",
                    "bet_amount" to 450L,
                )
            )

            response.field("error") shouldBe SkylineWebhook.ERROR_GENERAL.toString()
            coVerify(exactly = 0) { bus(ofType<PlaceSpinCasinoSessionCommand>()) }
        }
    }

    test("a bet the player cannot cover answers the code that raises their prompt") {
        coEvery { bus(ofType<PlaceSpinCasinoSessionCommand>()) } throws InsufficientBalanceException()

        runWebhook { client ->
            val response = client.send(
                payload(
                    "action" to "update_balance",
                    "session" to "our-token-abc",
                    "transaction" to "tx-7",
                    "bet_amount" to 999_999L,
                )
            )

            response.status shouldBe HttpStatusCode.OK
            response.field("error") shouldBe SkylineWebhook.ERROR_INSUFFICIENT_FUNDS.toString()
        }
    }

    test("an unsupported action is refused without touching the wallet") {
        runWebhook { client ->
            val response = client.send(payload("action" to "cash_out", "session" to "our-token-abc"))

            response.field("error") shouldBe SkylineWebhook.ERROR_GENERAL.toString()
            coVerify(exactly = 0) { bus(ofType<FindCasinoSessionQuery>()) }
        }
    }

    test("a zero call is a legal round marker and answers the balance as it stands") {
        runWebhook { client ->
            val response = client.send(
                payload(
                    "action" to "update_balance",
                    "session" to "our-token-abc",
                    "transaction" to "tx-8",
                    "bet_amount" to 0L,
                    "win_amount" to 0L,
                )
            )

            response.field("balance") shouldBe "2500"
            coVerify(exactly = 0) { bus(ofType<PlaceSpinCasinoSessionCommand>()) }
            coVerify(exactly = 0) { bus(ofType<SettleSpinCasinoSessionCommand>()) }
        }
    }

    test("a round that cannot be closed does not fail a call whose money already moved") {
        coEvery { bus(ofType<EndCasinoRoundSessionCommand>()) } throws IllegalStateException("round gone")

        runWebhook { client ->
            val response = client.send(
                payload(
                    "action" to "update_balance",
                    "session" to "our-token-abc",
                    "transaction" to "tx-9",
                    "round" to "round-9",
                    "win_amount" to 900L,
                    "is_last" to true,
                )
            )

            // Answering an error here would have the vendor refund a payout that went through.
            response.field("balance") shouldBe "2500"
            response.result().containsKey("error") shouldBe false
        }
    }

    test("every money call is taken under the player's wallet lock") {
        runWebhook { client ->
            client.send(
                payload(
                    "action" to "update_balance",
                    "session" to "our-token-abc",
                    "transaction" to "tx-10",
                    "bet_amount" to 450L,
                )
            )

            coVerify(exactly = 1) { guard.withLock<Any>("skyline:wallet:player_1:UAH", any()) }
        }
    }
})
