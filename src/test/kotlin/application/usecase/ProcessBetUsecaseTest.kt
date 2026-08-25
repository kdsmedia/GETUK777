package application.usecase

import application.port.external.IEventPublisherPort
import application.port.external.IWalletPort
import domain.model.AggregatorType
import domain.model.Bet
import domain.model.BetStatus
import domain.model.BetType
import domain.model.PlayerBalance
import domain.model.SportbookSession
import domain.repository.IBetRepository
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.ExternalBetId
import domain.vo.PlayerId
import domain.vo.SportbookSessionToken
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import support.TestFixtures

/**
 * The sportbook spends REAL money and nothing else: a stake is withdrawn from the real balance,
 * so everything a settlement moves goes to and comes from the same place. These cover the two
 * paths that used to reach the bonus wallet anyway.
 */
class ProcessBetUsecaseTest : FunSpec({

    val currency = Currency("UAH")

    fun session() = SportbookSession(
        token = SportbookSessionToken("T"),
        playerId = PlayerId("1"),
        currency = currency,
        aggregator = TestFixtures.aggregator(integration = "01TECHSPORT", type = AggregatorType.SPORTBOOK),
        data = emptyMap(),
    )

    fun bet() = Bet(
        externalId = ExternalBetId("bet-1"),
        playerId = PlayerId("1"),
        session = session(),
        currency = currency,
        betAmount = Amount(4_000_000_000),
        type = BetType.COMBO,
        selections = emptyList(),
    )

    test("a winning settlement lands entirely on the real balance") {
        val betRepo = mockk<IBetRepository>()
        val wallet = mockk<IWalletPort>(relaxed = true)
        val usecase = ProcessBetUsecase(betRepo, wallet, mockk<IEventPublisherPort>(relaxed = true))

        coEvery { betRepo.findByExternalId(ExternalBetId("bet-1")) } returns bet()
        coEvery { betRepo.save(any()) } answers { firstArg() }

        // 436.12 win + 30.25 accumulator bonus, as one figure — the vendor itemises them, the
        // wallet must not.
        val result = usecase.settle(
            externalId = "bet-1",
            transactionId = "467292591",
            currency = currency,
            amount = Amount(466_370_000_000),
            credit = true,
            won = true,
        )

        result.isSuccess shouldBe true
        result.getOrThrow().bet.status shouldBe BetStatus.WON
        result.getOrThrow().bet.winAmount shouldBe Amount(466_370_000_000)

        coVerify(exactly = 1) {
            wallet.deposit(
                PlayerId("1"),
                "sportbook:settle:467292591",
                currency,
                Amount(466_370_000_000),
                Amount.ZERO,
            )
        }
    }

    test("a clawback bigger than the real balance never touches bonus and reports the rest as debt") {
        val betRepo = mockk<IBetRepository>()
        val wallet = mockk<IWalletPort>(relaxed = true)
        val usecase = ProcessBetUsecase(betRepo, wallet, mockk<IEventPublisherPort>(relaxed = true))

        coEvery { betRepo.findByExternalId(ExternalBetId("bet-1")) } returns bet()
        coEvery { betRepo.save(any()) } answers { firstArg() }
        coEvery { wallet.findBalance(PlayerId("1"), currency) } returns PlayerBalance(
            realAmount = Amount(10_000_000_000),
            bonusAmount = Amount(30_250_000_000),
            currency = currency,
        )

        val result = usecase.settle(
            externalId = "bet-1",
            transactionId = "467292591",
            currency = currency,
            amount = Amount(25_000_000_000),
            credit = false,
            won = false,
        )

        result.isSuccess shouldBe true
        // 25 required, 10 real on hand: the 30.25 of bonus is not eligible, so 15 stays as debt.
        result.getOrThrow().debt shouldBe Amount(15_000_000_000)

        coVerify(exactly = 1) {
            wallet.withdraw(
                PlayerId("1"),
                "sportbook:settle:467292591",
                currency,
                Amount(10_000_000_000),
                Amount.ZERO,
            )
        }
    }
})
