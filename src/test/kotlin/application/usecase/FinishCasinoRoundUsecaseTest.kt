package application.usecase

import application.port.external.IEventPublisherPort
import domain.event.CasinoRoundEvent
import domain.exception.conflict.CasinoRoundAlreadyFinishedException
import domain.repository.ICasinoRoundRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import support.TestFixtures

class FinishCasinoRoundUsecaseTest : FunSpec({

    test("successful finish saves round and publishes CasinoRoundEvent") {
        val roundRepo = mockk<ICasinoRoundRepository>()
        val eventPublisher = mockk<IEventPublisherPort>(relaxed = true)
        val publishedSlot = slot<CasinoRoundEvent>()
        every { eventPublisher.publish(capture(publishedSlot)) } returns Unit
        coEvery { roundRepo.save(any()) } answers { firstArg() }

        val usecase = FinishCasinoRoundUsecase(roundRepo, eventPublisher)
        val round = TestFixtures.round()

        val result = usecase.invoke(round)

        result.isSuccess shouldBe true
        publishedSlot.captured.data.isFinished shouldBe true
        coVerify(exactly = 1) { roundRepo.save(match { it.isFinished }) }
    }

    test("finishing an already-finished round returns failure and does not publish") {
        val roundRepo = mockk<ICasinoRoundRepository>(relaxed = true)
        val eventPublisher = mockk<IEventPublisherPort>(relaxed = true)
        val usecase = FinishCasinoRoundUsecase(roundRepo, eventPublisher)

        val finishedOnce = TestFixtures.round().finish()

        val result = usecase.invoke(finishedOnce)

        result.isFailure shouldBe true
        (result.exceptionOrNull() is CasinoRoundAlreadyFinishedException) shouldBe true
        coVerify(exactly = 0) { roundRepo.save(any()) }
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }
})
