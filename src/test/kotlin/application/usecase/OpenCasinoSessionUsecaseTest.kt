package application.usecase

import application.port.external.IEventPublisherPort
import application.port.external.ICasinoGamePort
import application.port.factory.IAggregatorFactory
import domain.event.CasinoSessionEvent
import domain.repository.IAggregatorRepository
import domain.repository.IFreespinRepository
import domain.repository.ICasinoSessionRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import support.TestFixtures

class OpenCasinoSessionUsecaseTest : FunSpec({

    test("happy path fetches launch URL, saves session, publishes CasinoSessionEvent") {
        val aggregatorFactory = mockk<IAggregatorFactory>()
        val aggregatorRepo = mockk<IAggregatorRepository>()
            .also { coEvery { it.findByIntegration(any()) } returns TestFixtures.aggregator() }
        val gamePort = mockk<ICasinoGamePort>()
        val sessionRepo = mockk<ICasinoSessionRepository>()
        val eventPublisher = mockk<IEventPublisherPort>(relaxed = true)
        val freespinRepo = mockk<IFreespinRepository>().also { coEvery { it.findRedeemable(any(), any(), any()) } returns null }

        val session = TestFixtures.session()

        coEvery { aggregatorFactory.createGameAdapter(any()) } returns gamePort
        coEvery { gamePort.getLaunchUrl(session, "lobby", null) } returns ICasinoGamePort.Launch("https://launch.url")
        coEvery { sessionRepo.save(session) } returns session

        val usecase = OpenCasinoSessionUsecase(aggregatorFactory, aggregatorRepo, sessionRepo, freespinRepo, eventPublisher)

        val result = usecase.invoke(session, "lobby")

        result.isSuccess shouldBe true
        result.getOrThrow().launchUrl shouldBe "https://launch.url"
        result.getOrThrow().session shouldBe session
        verify(exactly = 1) {
            eventPublisher.publish(match<CasinoSessionEvent> { it.data.playerId == session.playerId })
        }
    }

    test("a provider-minted session id is persisted as the session's external token") {
        val aggregatorFactory = mockk<IAggregatorFactory>()
        val aggregatorRepo = mockk<IAggregatorRepository>()
            .also { coEvery { it.findByIntegration(any()) } returns TestFixtures.aggregator() }
        val gamePort = mockk<ICasinoGamePort>()
        val sessionRepo = mockk<ICasinoSessionRepository>()
        val eventPublisher = mockk<IEventPublisherPort>(relaxed = true)
        val freespinRepo = mockk<IFreespinRepository>().also { coEvery { it.findRedeemable(any(), any(), any()) } returns null }

        val session = TestFixtures.session()

        coEvery { aggregatorFactory.createGameAdapter(any()) } returns gamePort
        coEvery { gamePort.getLaunchUrl(any(), any(), any()) } returns
            ICasinoGamePort.Launch(url = "https://q31d0lghlxf67ep.gamix.party/", externalToken = "q31d0lghlxf67ep")
        coEvery { sessionRepo.save(any()) } answers { firstArg() }

        val usecase = OpenCasinoSessionUsecase(aggregatorFactory, aggregatorRepo, sessionRepo, freespinRepo, eventPublisher)

        val result = usecase.invoke(session, "lobby")

        result.getOrThrow().session.externalToken shouldBe "q31d0lghlxf67ep"
        coVerify(exactly = 1) { sessionRepo.save(match { it.externalToken == "q31d0lghlxf67ep" }) }
    }

    test("a provider that mints no session id is saved once, not twice") {
        val aggregatorFactory = mockk<IAggregatorFactory>()
        val aggregatorRepo = mockk<IAggregatorRepository>()
            .also { coEvery { it.findByIntegration(any()) } returns TestFixtures.aggregator() }
        val gamePort = mockk<ICasinoGamePort>()
        val sessionRepo = mockk<ICasinoSessionRepository>()
        val eventPublisher = mockk<IEventPublisherPort>(relaxed = true)
        val freespinRepo = mockk<IFreespinRepository>().also { coEvery { it.findRedeemable(any(), any(), any()) } returns null }

        val session = TestFixtures.session()

        coEvery { aggregatorFactory.createGameAdapter(any()) } returns gamePort
        coEvery { gamePort.getLaunchUrl(any(), any(), any()) } returns ICasinoGamePort.Launch("https://launch.url")
        coEvery { sessionRepo.save(any()) } answers { firstArg() }

        val usecase = OpenCasinoSessionUsecase(aggregatorFactory, aggregatorRepo, sessionRepo, freespinRepo, eventPublisher)

        usecase.invoke(session, "lobby").getOrThrow()

        coVerify(exactly = 1) { sessionRepo.save(any()) }
    }

    test("failing adapter propagates through runCatching as failure Result") {
        val aggregatorFactory = mockk<IAggregatorFactory>()
        val aggregatorRepo = mockk<IAggregatorRepository>()
            .also { coEvery { it.findByIntegration(any()) } returns TestFixtures.aggregator() }
        val gamePort = mockk<ICasinoGamePort>()
        val sessionRepo = mockk<ICasinoSessionRepository>(relaxed = true)
        val eventPublisher = mockk<IEventPublisherPort>(relaxed = true)
        val freespinRepo = mockk<IFreespinRepository>().also { coEvery { it.findRedeemable(any(), any(), any()) } returns null }

        val session = TestFixtures.session()

        coEvery { aggregatorFactory.createGameAdapter(any()) } returns gamePort
        coEvery { gamePort.getLaunchUrl(any(), any(), any()) } throws RuntimeException("upstream down")

        val usecase = OpenCasinoSessionUsecase(aggregatorFactory, aggregatorRepo, sessionRepo, freespinRepo, eventPublisher)

        val result = usecase.invoke(session, "lobby")

        result.isFailure shouldBe true
        // session is persisted BEFORE getLaunchUrl (TONGame save-before-launch ordering),
        // so the save happened; only the post-launch event publish is skipped on failure.
        coVerify(exactly = 1) { sessionRepo.save(session) }
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }
})
