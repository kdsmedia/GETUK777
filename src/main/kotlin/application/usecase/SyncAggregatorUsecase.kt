package application.usecase

import application.port.factory.IAggregatorFactory
import domain.repository.IGameRepository
import domain.repository.IGameVariantRepository
import domain.repository.IProviderRepository
import domain.model.Aggregator
import domain.model.Game
import domain.model.GameVariant
import domain.model.Provider
import domain.vo.GameSymbol
import domain.vo.Identity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

class SyncAggregatorUsecase(
    private val aggregatorFactory: IAggregatorFactory,
    private val gameRepository: IGameRepository,
    private val gameVariantRepository: IGameVariantRepository,
    private val providerRepository: IProviderRepository
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

        val updateGames = LinkedHashMap<Identity, Game>()
        val updatedVariants = mutableListOf<GameVariant>()
        var newProviders = 0
        var newGames = 0
        var reusedGames = 0
        var updatedVariantsCount = 0

        for (aggregatorGame in aggregatorGames) {
            val providerIdentity = resolveProviderIdentity(aggregatorGame.providerName, aliases)

            var provider = allProvidersAsync.await().firstOrNull { it.identity == providerIdentity }

            if (provider == null) {
                provider = Provider(
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

            // Tags are refreshed on every sync. Images are NOT the aggregator's to set —
            // artwork is uploaded by the operator through GameService.UpdateImage.
            // An existing game is reused as-is — same row, same provider, same artwork — and this
            // aggregator only contributes another variant below. Re-pointing its provider would
            // yank the game away from whichever aggregator currently serves it.
            val game = updateGames[gameIdentity]
                ?: existingGame?.copy(
                    tags = aggregatorGame.tags.ifEmpty { existingGame.tags },
                )?.also { reusedGames++ }
                ?: Game(
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
                ?: GameVariant(
                    symbol = GameSymbol(aggregatorGame.symbol),
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
     * Canonical provider identity for a name as this aggregator spells it.
     *
     * Providers are matched by identity, and identity is derived from the aggregator's own spelling
     * — so the same vendor listed as `egt` by one aggregator and `amusnet` by another looks like two
     * vendors, and every one of its games gets duplicated under a second provider. No amount of
     * identity comparison can see through that, because the names genuinely differ. The alias map
     * (aggregator config key `providerAliases`, e.g. `{"egt": "amusnet"}`) is what collapses them:
     * once the identity resolves to the one already in the database, the existing provider and its
     * games are reused and this aggregator only adds variants.
     */
    private fun resolveProviderIdentity(providerName: String, aliases: Map<String, String>): Identity {
        val declared = Identity.generate(providerName)
        return aliases[declared.value]?.let { Identity(it) } ?: declared
    }

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