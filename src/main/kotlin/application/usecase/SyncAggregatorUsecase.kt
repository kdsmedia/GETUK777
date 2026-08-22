package application.usecase

import application.port.factory.IAggregatorFactory
import domain.repository.ICasinoGameRepository
import domain.repository.ICasinoGameVariantRepository
import domain.repository.ICasinoProviderRepository
import domain.model.Aggregator
import domain.model.CasinoGame
import domain.model.CasinoGameVariant
import domain.model.CasinoProvider
import domain.service.CasinoProviderMatcher
import domain.vo.CasinoGameSymbol
import domain.vo.Identity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

class SyncAggregatorUsecase(
    private val aggregatorFactory: IAggregatorFactory,
    private val gameRepository: ICasinoGameRepository,
    private val gameVariantRepository: ICasinoGameVariantRepository,
    private val providerRepository: ICasinoProviderRepository
) {

    private val logger = LoggerFactory.getLogger(SyncAggregatorUsecase::class.java)

    suspend operator fun invoke(aggregator: Aggregator) = process(aggregator)

    private suspend fun process(aggregator: Aggregator) = coroutineScope {
        val id = aggregator.identity.value
        val gameAdapter = aggregatorFactory.createGameAdapter(aggregator)

        logger.info("[{}] fetching games from adapter ({})...", id, aggregator.integration)

        val aggregatorGamesAsync = async { gameAdapter.getAggregatorGames() }
        val gamesAsync = async { gameRepository.findAll() }
        val variantsAsync = async { gameVariantRepository.findAllByIntegration(aggregator.integration) }
        val allProvidersAsync = async { providerRepository.findAll().toMutableList() }

        val aggregatorGames = aggregatorGamesAsync.await()
        logger.info("[{}] adapter returned {} games", id, aggregatorGames.size)

        val existingGames = gamesAsync.await().associateBy { it.identity }
        val existingVariants = variantsAsync.await()

        val aliases = providerAliases(aggregator)

        // Provider resolution runs once per distinct spelling in the feed, not once per game: the
        // catalog-overlap check needs the whole feed catalog for that spelling, which a per-game
        // pass cannot see.
        val feedCatalogs = aggregatorGames
            .groupBy { it.providerName }
            .mapValues { (_, games) -> games.mapTo(mutableSetOf()) { it.name.lowercase() } }

        val existingCatalogs = existingGames.values
            .groupBy { it.provider.identity }
            .mapValues { (_, games) -> games.mapTo(mutableSetOf()) { it.name.lowercase() } }

        val resolutions = feedCatalogs.mapValues { (providerName, feedCatalog) ->
            resolveProvider(
                providerName = providerName,
                aliases = aliases,
                providers = allProvidersAsync.await(),
                feedCatalog = feedCatalog,
                existingCatalogs = existingCatalogs,
            )
        }

        // A spelling matched by name shape is written onto the provider as an alias, so the next
        // run resolves it outright instead of re-deriving it from the catalogs.
        for (resolution in resolutions.values) {
            val alias = resolution.learnedAlias ?: continue
            val provider = allProvidersAsync.await()
                .firstOrNull { it.identity == resolution.identity && alias !in it.aliases }
                ?: continue

            val updated = providerRepository.save(provider.copy(aliases = provider.aliases + alias))
            allProvidersAsync.await().replaceAll { if (it.identity == updated.identity) updated else it }

            logger.info("[{}] provider alias learned: {} -> {}", id, alias, updated.identity.value)
        }

        val updateGames = LinkedHashMap<Identity, CasinoGame>()
        val updatedVariants = mutableListOf<CasinoGameVariant>()
        var newProviders = 0
        var newGames = 0
        var reusedGames = 0
        var updatedVariantsCount = 0

        for (aggregatorGame in aggregatorGames) {
            val providerIdentity = resolutions.getValue(aggregatorGame.providerName).identity

            var provider = allProvidersAsync.await().firstOrNull { it.identity == providerIdentity }

            if (provider == null) {
                provider = CasinoProvider(
                    identity = providerIdentity,
                    name = aggregatorGame.providerName,
                    aggregator = aggregator
                ).let { providerRepository.save(it) }

                allProvidersAsync.await().add(provider)
                newProviders++
                logger.info("[{}] new provider: identity={}", id, providerIdentity.value)
            }

            val gameIdentity = Identity.generate("${providerIdentity}_${aggregatorGame.name}")

            val existingGame = existingGames[gameIdentity]

            // Aggregator tags are MERGED into whatever the row already carries, never substituted:
            // the operator's editorial tags (hot/new/top) live in the same list and a plain
            // overwrite would erase them on every run. Images are NOT the aggregator's to set —
            // artwork is uploaded by the operator through CasinoGameService.UpdateImage.
            // An existing game is reused as-is — same row, same provider, same artwork — and this
            // aggregator only contributes another variant below. Re-pointing its provider would
            // yank the game away from whichever aggregator currently serves it.
            val game = updateGames[gameIdentity]
                ?: existingGame?.copy(
                    tags = (aggregatorGame.tags + existingGame.tags).distinct(),
                )?.also { reusedGames++ }
                ?: CasinoGame(
                    identity = gameIdentity,
                    name = aggregatorGame.name,
                    provider = provider,
                    tags = aggregatorGame.tags,
                ).also { newGames++ }

            updateGames[gameIdentity] = game

            val existingVariant = existingVariants
                .find { it.symbol.value == aggregatorGame.symbol && it.integration == aggregator.integration }

            val variant = existingVariant
                ?.copy(
                    providerName = aggregatorGame.providerName,
                    freeSpinEnable = aggregatorGame.freeSpinEnable,
                    freeChipEnable = aggregatorGame.freeChipEnable,
                    jackpotEnable = aggregatorGame.jackpotEnable,
                    demoEnable = aggregatorGame.demoEnable,
                    bonusBuyEnable = aggregatorGame.bonusBuyEnable,
                    platforms = aggregatorGame.platforms,
                    locales = aggregatorGame.locales,
                    playLines = aggregatorGame.playLines,
                )
                ?.also { updatedVariantsCount++ }
                ?: CasinoGameVariant(
                    symbol = CasinoGameSymbol(aggregatorGame.symbol),
                    name = aggregatorGame.name,
                    integration = aggregator.integration,
                    game = game,
                    providerName = aggregatorGame.providerName,
                    freeSpinEnable = aggregatorGame.freeSpinEnable,
                    freeChipEnable = aggregatorGame.freeChipEnable,
                    jackpotEnable = aggregatorGame.jackpotEnable,
                    demoEnable = aggregatorGame.demoEnable,
                    bonusBuyEnable = aggregatorGame.bonusBuyEnable,
                    locales = aggregatorGame.locales,
                    platforms = aggregatorGame.platforms,
                    playLines = aggregatorGame.playLines,
                )

            updatedVariants.add(variant)
        }

        logger.info(
            "[{}] summary: {} fetched, {} new games, {} reused games, {} new providers, " +
                "{} existing variants refreshed, {} variants total",
            id, aggregatorGames.size, newGames, reusedGames, newProviders,
            updatedVariantsCount, updatedVariants.size,
        )

        gameRepository.saveAll(updateGames.values.toList())
        gameVariantRepository.saveAll(updatedVariants)

        logger.info("[{}] persisted: {} games, {} variants", id, updateGames.size, updatedVariants.size)
    }

    /**
     * Canonical provider for a name as this aggregator spells it.
     *
     * Providers are matched by identity, and identity is derived from the aggregator's own spelling
     * — so the same vendor listed as `egt` by one aggregator and `amusnet` by another looks like two
     * vendors, and every one of its games gets duplicated under a second provider. Four ways out,
     * in descending order of how explicit they are:
     *
     *  1. the configured alias map (aggregator config key `providerAliases`, e.g. `{"egt":"amusnet"}`)
     *     — a human said so, it wins;
     *  2. an alias already recorded on a provider row, which is how step 4 remembers its own work;
     *  3. an exact identity match;
     *  4. [CasinoProviderMatcher]: same normalized name AND overlapping catalogs. This is what
     *     catches the pairs nobody thought to configure — `pragmatic` next to `pragmatic_play` sat
     *     unmerged and unusable until it did.
     *
     * Anything else is a genuinely new vendor and gets its own provider.
     */
    private fun resolveProvider(
        providerName: String,
        aliases: Map<String, String>,
        providers: List<CasinoProvider>,
        feedCatalog: Set<String>,
        existingCatalogs: Map<Identity, Set<String>>,
    ): Resolution {
        val declared = Identity.generate(providerName)

        aliases[declared.value]?.let { return Resolution(Identity(it)) }

        providers.firstOrNull { declared.value in it.aliases }
            ?.let { return Resolution(it.identity) }

        providers.firstOrNull { it.identity == declared }
            ?.let { return Resolution(it.identity) }

        providers.firstOrNull {
            CasinoProviderMatcher.isSameVendor(
                leftName = it.identity.value,
                rightName = declared.value,
                leftCatalog = existingCatalogs[it.identity].orEmpty(),
                rightCatalog = feedCatalog,
            )
        }?.let { return Resolution(it.identity, learnedAlias = declared.value) }

        return Resolution(declared)
    }

    private data class Resolution(val identity: Identity, val learnedAlias: String? = null)

    private fun providerAliases(aggregator: Aggregator): Map<String, String> {
        val configured = aggregator.config[PROVIDER_ALIASES] as? Map<*, *> ?: return emptyMap()

        return configured.entries.mapNotNull { (key, value) ->
            val from = key?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val to = value?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            from to to
        }.toMap()
    }

    private companion object {
        const val PROVIDER_ALIASES = "providerAliases"
    }
}