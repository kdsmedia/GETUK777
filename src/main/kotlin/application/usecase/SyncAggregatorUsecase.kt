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
import domain.vo.ImageMap
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

        val updateGames = LinkedHashMap<Identity, Game>()
        val updatedVariants = mutableListOf<GameVariant>()
        var newProviders = 0
        var newGames = 0
        var updatedVariantsCount = 0

        for (aggregatorGame in aggregatorGames) {
            val providerIdentity = Identity.generate(aggregatorGame.providerName)

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

            // Catalog metadata (tags, artwork) is refreshed on every sync, but operator-owned
            // state — active, order, custom image keys — is carried over untouched.
            val game = updateGames[gameIdentity]
                ?: existingGame?.copy(
                    tags = aggregatorGame.tags.ifEmpty { existingGame.tags },
                    images = ImageMap(existingGame.images.data + aggregatorGame.images),
                )
                ?: Game(
                    identity = gameIdentity,
                    name = aggregatorGame.name,
                    provider = provider,
                    tags = aggregatorGame.tags,
                    images = ImageMap(aggregatorGame.images),
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
            "[{}] summary: {} fetched, {} new games, {} new providers, {} existing variants refreshed, {} variants total",
            id, aggregatorGames.size, newGames, newProviders, updatedVariantsCount, updatedVariants.size,
        )

        gameRepository.saveAll(updateGames.values.toList())
        gameVariantRepository.saveAll(updatedVariants)

        logger.info("[{}] persisted: {} games, {} variants", id, updateGames.size, updatedVariants.size)
    }

}