package application.usecase

import application.port.external.IGamePort
import application.port.factory.IAggregatorFactory
import domain.repository.ISessionRepository
import event.AppEventPublisher
import event.SessionEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import support.TestFixtures

class OpenSessionUsecaseTest : FunSpec({

    test("happy path fetches launch URL, saves session, publishes SessionEvent") {
        val aggregatorFactory = mockk<IAggregatorFactory>()
        val gamePort = mockk<IGamePort>()
        val sessionRepo = mockk<ISessionRepository>()
        val eventPublisher = mockk<AppEventPublisher>(relaxed = true)

        val session = TestFixtures.session()

        coEvery { aggregatorFactory.createGameAdapter(any()) } returns gamePort
        coEvery { gamePort.getLaunchUrl(session, "lobby") } returns "https://launch.url"
        coEvery { sessionRepo.save(session) } returns session

        val usecase = OpenSessionUsecase(aggregatorFactory, sessionRepo, eventPublisher)

        val result = usecase.invoke(session, "lobby")

        result.isSuccess shouldBe true
        result.getOrThrow().launchUrl shouldBe "https://launch.url"
        result.getOrThrow().session shouldBe session
        verify(exactly = 1) {
            eventPublisher.publish(match<SessionEvent> { it.data.playerId == session.playerId.value })
        }
    }

    test("failing adapter propagates through runCatching as failure Result") {
        val aggregatorFactory = mockk<IAggregatorFactory>()
        val gamePort = mockk<IGamePort>()
        val sessionRepo = mockk<ISessionRepository>(relaxed = true)
        val eventPublisher = mockk<AppEventPublisher>(relaxed = true)

        val session = TestFixtures.session()

        coEvery { aggregatorFactory.createGameAdapter(any()) } returns gamePort
        coEvery { gamePort.getLaunchUrl(any(), any()) } throws RuntimeException("upstream down")

        val usecase = OpenSessionUsecase(aggregatorFactory, sessionRepo, eventPublisher)

        val result = usecase.invoke(session, "lobby")

        result.isFailure shouldBe true
        // session is persisted BEFORE getLaunchUrl (TONGame save-before-launch ordering),
        // so the save happened; only the post-launch event publish is skipped on failure.
        coVerify(exactly = 1) { sessionRepo.save(session) }
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }
})
