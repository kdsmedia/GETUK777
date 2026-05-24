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

        val updateGames = mutableListOf<Game>()
        val updatedVariants = mutableListOf<GameVariant>()
        var newProviders = 0
        var newGames = 0
        var updatedVariantsCount = 0

        for (aggregatorGame in aggregatorGames) {
            var variant = variantsAsync.await()
                .find { it.symbol.value == aggregatorGame.symbol && it.integration == aggregator.integration }

            if (variant != null) {
                updatedVariantsCount++
                updatedVariants.add(variant.copy(
                    freeSpinEnable = aggregatorGame.freeSpinEnable,
                    freeChipEnable = aggregatorGame.freeChipEnable,
                    jackpotEnable = aggregatorGame.jackpotEnable,
                    demoEnable = aggregatorGame.demoEnable,
                    bonusBuyEnable = aggregatorGame.bonusBuyEnable,
                    platforms = aggregatorGame.platforms,
                    locales = aggregatorGame.locales,
                    playLines = aggregatorGame.playLines,
                ))
                continue
            }

            val providerIdentity = Identity.generate(aggregatorGame.providerName)

            var provider = allProvidersAsync.await().firstOrNull { it.identity == providerIdentity }

            if (provider == null) {
                provider = Provider(
                    identity = providerIdentity,
                    name = aggregatorGame.name,
                    aggregator = aggregator
                ).let { providerRepository.save(it) }

                allProvidersAsync.await().add(provider)
                newProviders++
                logger.info("[{}] new provider: identity={}", id, providerIdentity.value)
            }

            val gameIdentity = Identity.generate("${providerIdentity}_${aggregatorGame.name}")

            var game = gamesAsync.await().find { it.identity == gameIdentity }

            if (game == null) {
                game = Game(
                    identity = gameIdentity,
                    name = aggregatorGame.name,
                    provider = provider,
                )

                updateGames.add(game)
                newGames++
            }

            variant = GameVariant(
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

        gameRepository.saveAll(updateGames)
        gameVariantRepository.saveAll(updatedVariants)

        logger.info("[{}] persisted: {} games, {} variants", id, updateGames.size, updatedVariants.size)
    }

}