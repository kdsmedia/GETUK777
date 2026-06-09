package application.usecase

import application.port.external.IEventPublisherPort
import domain.event.RoundEvent
import domain.exception.conflict.RoundAlreadyFinishedException
import domain.repository.IRoundRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import support.TestFixtures

class FinishRoundUsecaseTest : FunSpec({

    test("successful finish saves round and publishes RoundEvent") {
        val roundRepo = mockk<IRoundRepository>()
        val eventPublisher = mockk<IEventPublisherPort>(relaxed = true)
        val publishedSlot = slot<RoundEvent>()
        every { eventPublisher.publish(capture(publishedSlot)) } returns Unit
        coEvery { roundRepo.save(any()) } answers { firstArg() }

        val usecase = FinishRoundUsecase(roundRepo, eventPublisher)
        val round = TestFixtures.round()

        val result = usecase.invoke(round)

        result.isSuccess shouldBe true
        publishedSlot.captured.data.isFinished shouldBe true
        coVerify(exactly = 1) { roundRepo.save(match { it.isFinished }) }
    }

    test("finishing an already-finished round returns failure and does not publish") {
        val roundRepo = mockk<IRoundRepository>(relaxed = true)
        val eventPublisher = mockk<IEventPublisherPort>(relaxed = true)
        val usecase = FinishRoundUsecase(roundRepo, eventPublisher)

        val finishedOnce = TestFixtures.round().finish()

        val result = usecase.invoke(finishedOnce)

        result.isFailure shouldBe true
        (result.exceptionOrNull() is RoundAlreadyFinishedException) shouldBe true
        coVerify(exactly = 0) { roundRepo.save(any()) }
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }
})
