package application.usecase

import application.port.external.IEventPublisherPort
import application.port.external.IGamePort
import application.port.factory.IAggregatorFactory
import domain.event.SessionEvent
import domain.repository.IFreespinRepository
import domain.repository.ISessionRepository
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
        val eventPublisher = mockk<IEventPublisherPort>(relaxed = true)
        val freespinRepo = mockk<IFreespinRepository>().also { coEvery { it.findRedeemable(any(), any(), any()) } returns null }

        val session = TestFixtures.session()

        coEvery { aggregatorFactory.createGameAdapter(any()) } returns gamePort
        coEvery { gamePort.getLaunchUrl(session, "lobby", null) } returns IGamePort.Launch("https://launch.url")
        coEvery { sessionRepo.save(session) } returns session

        val usecase = OpenSessionUsecase(aggregatorFactory, sessionRepo, freespinRepo, eventPublisher)

        val result = usecase.invoke(session, "lobby")

        result.isSuccess shouldBe true
        result.getOrThrow().launchUrl shouldBe "https://launch.url"
        result.getOrThrow().session shouldBe session
        verify(exactly = 1) {
            eventPublisher.publish(match<SessionEvent> { it.data.playerId == session.playerId })
        }
    }

    test("a provider-minted session id is persisted as the session's external token") {
        val aggregatorFactory = mockk<IAggregatorFactory>()
        val gamePort = mockk<IGamePort>()
        val sessionRepo = mockk<ISessionRepository>()
        val eventPublisher = mockk<IEventPublisherPort>(relaxed = true)
        val freespinRepo = mockk<IFreespinRepository>().also { coEvery { it.findRedeemable(any(), any(), any()) } returns null }

        val session = TestFixtures.session()

        coEvery { aggregatorFactory.createGameAdapter(any()) } returns gamePort
        coEvery { gamePort.getLaunchUrl(any(), any(), any()) } returns
            IGamePort.Launch(url = "https://q31d0lghlxf67ep.gamix.party/", externalToken = "q31d0lghlxf67ep")
        coEvery { sessionRepo.save(any()) } answers { firstArg() }

        val usecase = OpenSessionUsecase(aggregatorFactory, sessionRepo, freespinRepo, eventPublisher)

        val result = usecase.invoke(session, "lobby")

        result.getOrThrow().session.externalToken shouldBe "q31d0lghlxf67ep"
        coVerify(exactly = 1) { sessionRepo.save(match { it.externalToken == "q31d0lghlxf67ep" }) }
    }

    test("a provider that mints no session id is saved once, not twice") {
        val aggregatorFactory = mockk<IAggregatorFactory>()
        val gamePort = mockk<IGamePort>()
        val sessionRepo = mockk<ISessionRepository>()
        val eventPublisher = mockk<IEventPublisherPort>(relaxed = true)
        val freespinRepo = mockk<IFreespinRepository>().also { coEvery { it.findRedeemable(any(), any(), any()) } returns null }

        val session = TestFixtures.session()

        coEvery { aggregatorFactory.createGameAdapter(any()) } returns gamePort
        coEvery { gamePort.getLaunchUrl(any(), any(), any()) } returns IGamePort.Launch("https://launch.url")
        coEvery { sessionRepo.save(any()) } answers { firstArg() }

        val usecase = OpenSessionUsecase(aggregatorFactory, sessionRepo, freespinRepo, eventPublisher)

        usecase.invoke(session, "lobby").getOrThrow()

        coVerify(exactly = 1) { sessionRepo.save(any()) }
    }

    test("failing adapter propagates through runCatching as failure Result") {
        val aggregatorFactory = mockk<IAggregatorFactory>()
        val gamePort = mockk<IGamePort>()
        val sessionRepo = mockk<ISessionRepository>(relaxed = true)
        val eventPublisher = mockk<IEventPublisherPort>(relaxed = true)
        val freespinRepo = mockk<IFreespinRepository>().also { coEvery { it.findRedeemable(any(), any(), any()) } returns null }

        val session = TestFixtures.session()

        coEvery { aggregatorFactory.createGameAdapter(any()) } returns gamePort
        coEvery { gamePort.getLaunchUrl(any(), any(), any()) } throws RuntimeException("upstream down")

        val usecase = OpenSessionUsecase(aggregatorFactory, sessionRepo, freespinRepo, eventPublisher)

        val result = usecase.invoke(session, "lobby")

        result.isFailure shouldBe true
        // session is persisted BEFORE getLaunchUrl (TONGame save-before-launch ordering),
        // so the save happened; only the post-launch event publish is skipped on failure.
        coVerify(exactly = 1) { sessionRepo.save(session) }
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }
})
