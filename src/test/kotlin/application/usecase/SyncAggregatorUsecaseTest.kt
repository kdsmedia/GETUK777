package application.usecase

import application.port.external.ICasinoGamePort
import application.port.factory.IAggregatorFactory
import domain.model.Aggregator
import domain.model.CasinoGame
import domain.model.CasinoGameVariant
import domain.model.Platform
import domain.model.CasinoProvider
import domain.repository.ICasinoGameRepository
import domain.repository.ICasinoGameVariantRepository
import domain.repository.ICasinoProviderRepository
import domain.vo.Identity
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import support.TestFixtures

/**
 * The sync must never mint a second copy of a vendor that is already in the catalogue. Matching is by
 * provider identity, and identity comes from whatever the aggregator calls the vendor — so the same
 * studio spelled differently by two aggregators only collapses through the alias map.
 */
class SyncAggregatorUsecaseTest : FunSpec({

    fun aggregatorGame(
        symbol: String,
        name: String,
        providerName: String,
    ) = ICasinoGamePort.AggregatorGame(
        symbol = symbol,
        name = name,
        providerName = providerName,
        freeSpinEnable = false,
        freeChipEnable = false,
        jackpotEnable = false,
        demoEnable = true,
        bonusBuyEnable = false,
        locales = emptyList(),
        platforms = listOf(Platform.DESKTOP),
    )

    class Harness(
        existingProviders: List<CasinoProvider>,
        existingGames: List<CasinoGame>,
        val aggregator: Aggregator,
        games: List<ICasinoGamePort.AggregatorGame>,
    ) {
        val savedProviders = mutableListOf<CasinoProvider>()
        val savedGames = mutableListOf<List<CasinoGame>>()
        val savedVariants = mutableListOf<List<CasinoGameVariant>>()

        private val providerRepository = mockk<ICasinoProviderRepository>()
        private val gameRepository = mockk<ICasinoGameRepository>()
        private val variantRepository = mockk<ICasinoGameVariantRepository>()
        private val factory = mockk<IAggregatorFactory>()

        init {
            val port = mockk<ICasinoGamePort>()
            coEvery { port.getAggregatorGames() } returns games
            coEvery { factory.createGameAdapter(any()) } returns port

            coEvery { providerRepository.findAll() } returns existingProviders
            coEvery { providerRepository.save(any()) } answers {
                firstArg<CasinoProvider>().also { savedProviders += it }
            }
            coEvery { gameRepository.findAll() } returns existingGames
            coEvery { gameRepository.saveAll(any()) } answers {
                firstArg<List<CasinoGame>>().also { savedGames += it }
            }
            coEvery { variantRepository.findAllByIntegration(any()) } returns emptyList()
            coEvery { variantRepository.saveAll(any()) } answers {
                firstArg<List<CasinoGameVariant>>().also { savedVariants += it }
            }
        }

        suspend fun run() = SyncAggregatorUsecase(
            aggregatorFactory = factory,
            gameRepository = gameRepository,
            gameVariantRepository = variantRepository,
            providerRepository = providerRepository,
        ).invoke(aggregator)

        val persistedGames: List<CasinoGame> get() = savedGames.flatten()
        val persistedVariants: List<CasinoGameVariant> get() = savedVariants.flatten()
    }

    test("an aliased provider reuses the existing provider and its games, adding only a variant") {
        val incumbent = TestFixtures.aggregator(identity = "onegamehub", integration = "ONEGAMEHUB")
        val amusnet = TestFixtures.provider(identity = "amusnet", aggregator = incumbent)
        val existingGame = TestFixtures.game(identity = "amusnet_100_burning_hot", provider = amusnet)

        val harness = Harness(
            existingProviders = listOf(amusnet),
            existingGames = listOf(existingGame),
            // The new aggregator calls the very same studio "EGT".
            aggregator = TestFixtures.aggregator(
                identity = "gamingflow",
                integration = "GAMINGFLOW",
                config = mapOf("providerAliases" to mapOf("egt" to "amusnet")),
            ),
            games = listOf(aggregatorGame("bhot_100", "100 Burning Hot", "EGT")),
        )

        harness.run()

        // No second studio, no second game — only the variant that makes it launchable.
        harness.savedProviders.shouldBeEmpty()
        harness.persistedGames.map { it.identity.value } shouldContainExactly listOf("amusnet_100_burning_hot")
        harness.persistedVariants.map { it.integration } shouldContainExactly listOf("GAMINGFLOW")
        harness.persistedVariants.single().game.identity shouldBe existingGame.identity

        // The game keeps the provider that already serves it.
        harness.persistedGames.single().provider.identity.value shouldBe "amusnet"
    }

    test("without an alias the same studio is duplicated — this is what the alias map prevents") {
        val incumbent = TestFixtures.aggregator(identity = "onegamehub", integration = "ONEGAMEHUB")
        val amusnet = TestFixtures.provider(identity = "amusnet", aggregator = incumbent)
        val existingGame = TestFixtures.game(identity = "amusnet_100_burning_hot", provider = amusnet)

        val harness = Harness(
            existingProviders = listOf(amusnet),
            existingGames = listOf(existingGame),
            aggregator = TestFixtures.aggregator(identity = "gamingflow", integration = "GAMINGFLOW"),
            games = listOf(aggregatorGame("bhot_100", "100 Burning Hot", "EGT")),
        )

        harness.run()

        harness.savedProviders.map { it.identity.value } shouldContainExactly listOf("egt")
        harness.persistedGames.map { it.identity.value } shouldContainExactly listOf("egt_100_burning_hot")
    }

    test("a provider already present under its own name is reused without an alias") {
        val incumbent = TestFixtures.aggregator(identity = "onegamehub", integration = "ONEGAMEHUB")
        val bgaming = TestFixtures.provider(identity = "bgaming", aggregator = incumbent)
        val existingGame = TestFixtures.game(identity = "bgaming_lucky_lady", provider = bgaming)

        val harness = Harness(
            existingProviders = listOf(bgaming),
            existingGames = listOf(existingGame),
            aggregator = TestFixtures.aggregator(identity = "gamingflow", integration = "GAMINGFLOW"),
            games = listOf(aggregatorGame("lucky_lady_gf", "Lucky Lady", "bgaming")),
        )

        harness.run()

        harness.savedProviders.shouldBeEmpty()
        harness.persistedGames.map { it.identity.value } shouldContainExactly listOf("bgaming_lucky_lady")
        harness.persistedVariants.single().symbol.value shouldBe "lucky_lady_gf"
    }

    test("punctuation differences in a game title do not create a second game") {
        val incumbent = TestFixtures.aggregator(identity = "onegamehub", integration = "ONEGAMEHUB")
        val booongo = TestFixtures.provider(identity = "booongo", aggregator = incumbent)
        val existingGame = TestFixtures.game(identity = "booongo_book_of_sun_multichance", provider = booongo)

        val harness = Harness(
            existingProviders = listOf(booongo),
            existingGames = listOf(existingGame),
            aggregator = TestFixtures.aggregator(identity = "gamingflow", integration = "GAMINGFLOW"),
            // Same title, colon instead of nothing.
            games = listOf(aggregatorGame("bos_mc", "Book of Sun: Multichance", "booongo")),
        )

        harness.run()

        harness.persistedGames.map { it.identity.value } shouldContainExactly
            listOf("booongo_book_of_sun_multichance")
    }

    test("a genuinely new studio is still created") {
        val harness = Harness(
            existingProviders = emptyList(),
            existingGames = emptyList(),
            aggregator = TestFixtures.aggregator(identity = "gamingflow", integration = "GAMINGFLOW"),
            games = listOf(aggregatorGame("starburst", "Starburst", "netent")),
        )

        harness.run()

        harness.savedProviders.map { it.identity.value } shouldContainExactly listOf("netent")
        harness.persistedGames.map { it.identity.value } shouldContainExactly listOf("netent_starburst")
    }
    test("a duplicate vendor is merged on name shape when the catalogues agree, and the alias is remembered") {
        // The real case: GamingFlow calls Pragmatic "pragmatic", we already carry it from OneGameHub
        // as "pragmatic_play". Nobody had configured that alias, so it imported as a second studio
        // whose games were all inactive and unreachable.
        val incumbent = TestFixtures.aggregator(identity = "onegamehub", integration = "ONEGAMEHUB")
        val pragmatic = TestFixtures.provider(identity = "pragmatic_play", aggregator = incumbent)

        val harness = Harness(
            existingProviders = listOf(pragmatic),
            existingGames = listOf(
                TestFixtures.game(identity = "pragmatic_play_5_lions", provider = pragmatic),
                TestFixtures.game(identity = "pragmatic_play_gates_of_olympus", provider = pragmatic),
            ).mapIndexed { index, game -> game.copy(name = listOf("5 Lions", "Gates of Olympus")[index]) },
            aggregator = TestFixtures.aggregator(identity = "gamingflow", integration = "GAMINGFLOW"),
            games = listOf(aggregatorGame("5l_gf", "5 Lions", "Pragmatic")),
        )

        harness.run()

        // Reused, not duplicated.
        harness.persistedGames.map { it.identity.value } shouldContainExactly listOf("pragmatic_play_5_lions")
        harness.persistedVariants.single().integration shouldBe "GAMINGFLOW"

        // And written down, so the next run resolves it outright.
        harness.savedProviders.single().identity.value shouldBe "pragmatic_play"
        harness.savedProviders.single().aliases shouldContainExactly listOf("pragmatic")
    }

    test("a matching name with an unrelated catalogue is NOT merged") {
        val incumbent = TestFixtures.aggregator(identity = "onegamehub", integration = "ONEGAMEHUB")
        val pragmatic = TestFixtures.provider(identity = "pragmatic_play", aggregator = incumbent)

        val harness = Harness(
            existingProviders = listOf(pragmatic),
            existingGames = listOf(
                TestFixtures.game(identity = "pragmatic_play_gates_of_olympus", provider = pragmatic)
                    .copy(name = "Gates of Olympus"),
            ),
            aggregator = TestFixtures.aggregator(identity = "gamingflow", integration = "GAMINGFLOW"),
            games = listOf(aggregatorGame("unrelated", "Totally Different Game", "Pragmatic")),
        )

        harness.run()

        // Name shape alone is not evidence — a human decides this one.
        harness.savedProviders.map { it.identity.value } shouldContainExactly listOf("pragmatic")
    }

    test("a recorded alias resolves without re-deriving it") {
        val incumbent = TestFixtures.aggregator(identity = "onegamehub", integration = "ONEGAMEHUB")
        val pragmatic = TestFixtures.provider(identity = "pragmatic_play", aggregator = incumbent)
            .copy(aliases = listOf("pragmatic"))

        val harness = Harness(
            existingProviders = listOf(pragmatic),
            existingGames = listOf(
                TestFixtures.game(identity = "pragmatic_play_5_lions", provider = pragmatic).copy(name = "5 Lions"),
            ),
            aggregator = TestFixtures.aggregator(identity = "gamingflow", integration = "GAMINGFLOW"),
            // A title the incumbent does not carry: with no catalogue overlap to lean on, only the
            // recorded alias can resolve this.
            games = listOf(aggregatorGame("newone", "Brand New Title", "Pragmatic")),
        )

        harness.run()

        harness.savedProviders.shouldBeEmpty()
        harness.persistedGames.map { it.identity.value } shouldContainExactly
            listOf("pragmatic_play_brand_new_title")
    }
})