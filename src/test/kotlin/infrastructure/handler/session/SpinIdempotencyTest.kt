package infrastructure.handler.session

import application.command.session.PlaceSpinSessionCommand
import application.command.session.SettleSpinSessionCommand
import application.port.external.IEventPublisherPort
import application.port.external.IPlayerLimitPort
import application.port.external.IWalletPort
import application.usecase.ProcessSpinUsecase
import domain.exception.conflict.SpinAlreadyExistsException
import domain.model.PlayerBalance
import domain.model.Spin
import domain.repository.IRoundRepository
import domain.repository.ISpinRepository
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.PlayerId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import support.TestFixtures

/**
 * A concurrent redelivery of one provider transaction must move money once. The lookup that
 * opens each handler cannot see an insert still in flight, so the unique constraint on
 * `spins.external_id` is the real guard — these specs pin the behaviour that hangs off it.
 */
class SpinIdempotencyTest : FunSpec({

    val session = TestFixtures.session(currency = "UAH")
    val round = TestFixtures.round(session = session)
    val settledBalance = TestFixtures.balance(real = 900, bonus = 0, currency = "UAH")

    fun wallet() = mockk<IWalletPort>(relaxed = true).also {
        coEvery { it.findBalance(any(), any()) } returns settledBalance
    }

    fun usecase(walletPort: IWalletPort, spinRepository: ISpinRepository) = ProcessSpinUsecase(
        spinRepository = spinRepository,
        eventPublisher = mockk<IEventPublisherPort>(relaxed = true),
        walletPort = walletPort,
        playerLimitPort = mockk<IPlayerLimitPort>(relaxed = true).also {
            coEvery { it.getMaxPlaceAmount(any()) } returns null
        },
    )

    test("a PLACE that loses the unique-constraint race answers with the settled balance") {
        val walletPort = wallet()
        val spinRepository = mockk<ISpinRepository>()
        val roundRepository = mockk<IRoundRepository>()

        coEvery { spinRepository.findByExternalId(any()) } returns null
        coEvery { spinRepository.save(any()) } throws SpinAlreadyExistsException()
        coEvery { roundRepository.findByExternalIdAndSessionId(any(), any()) } returns round

        val handler = PlaceSpinSessionHandler(
            roundRepository = roundRepository,
            spinRepository = spinRepository,
            processSpinUsecase = usecase(walletPort, spinRepository),
            walletPort = walletPort,
        )

        val result = handler.handle(
            PlaceSpinSessionCommand(
                session = session,
                gameSymbol = "elvisfrog_bg",
                externalRoundId = "round_1",
                externalSpinId = "ref-1:place",
                amount = Amount(100),
            )
        )

        result.isSuccess shouldBe true
        result.getOrThrow() shouldBe settledBalance
    }

    test("a SETTLE that loses the unique-constraint race answers with the settled balance") {
        val walletPort = wallet()
        val spinRepository = mockk<ISpinRepository>()
        val roundRepository = mockk<IRoundRepository>()

        coEvery { spinRepository.findByExternalId(any()) } returns null
        coEvery { spinRepository.save(any()) } throws SpinAlreadyExistsException()
        coEvery { roundRepository.findByExternalIdAndSessionId(any(), any()) } returns round

        val handler = SettleSpinSessionHandler(
            roundRepository = roundRepository,
            spinRepository = spinRepository,
            processSpinUsecase = usecase(walletPort, spinRepository),
            walletPort = walletPort,
        )

        val result = handler.handle(
            SettleSpinSessionCommand(
                session = session,
                gameSymbol = "elvisfrog_bg",
                externalRoundId = "round_1",
                externalSpinId = "ref-1:settle",
                amount = Amount(100),
            )
        )

        result.isSuccess shouldBe true
        result.getOrThrow() shouldBe settledBalance
    }

    test("the wallet move completes before the spin row is written") {
        val order = mutableListOf<String>()

        // Hand-rolled rather than mocked: the wallet signature mixes value classes with plain
        // params, which mockk cannot build a matcher for.
        val walletPort = object : IWalletPort {
            override suspend fun findBalance(playerId: PlayerId, currency: Currency) = settledBalance

            override suspend fun withdraw(
                playerId: PlayerId,
                transactionId: String,
                currency: Currency,
                realAmount: Amount,
                bonusAmount: Amount,
            ): PlayerBalance {
                order += "wallet"
                return settledBalance
            }

            override suspend fun deposit(
                playerId: PlayerId,
                transactionId: String,
                currency: Currency,
                realAmount: Amount,
                bonusAmount: Amount,
            ): PlayerBalance {
                order += "wallet"
                return settledBalance
            }
        }

        val spinRepository = mockk<ISpinRepository>()
        coEvery { spinRepository.save(any()) } answers { order += "spin"; firstArg<Spin>() }

        usecase(walletPort, spinRepository).invoke(TestFixtures.spin(round = round)).getOrThrow()

        // The other way round, a redelivery that collides on the unique index would read a wallet
        // that has not caught up and report a balance the player never had.
        order shouldBe listOf("wallet", "spin")
    }
})
