package infrastructure.handler.game

import application.ICommandHandler
import application.command.game.PlayCasinoGameCommand
import application.command.game.PlayCasinoGameResult
import application.port.external.IPlayerLimitPort
import application.usecase.OpenCasinoSessionUsecase
import domain.exception.domainRequireNotNull
import domain.exception.notfound.AggregatorNotFoundException
import domain.exception.notfound.CasinoGameNotFoundException
import domain.repository.IAggregatorRepository
import domain.repository.ICasinoGameVariantRepository
import domain.service.CasinoSessionFactory
import domain.vo.CasinoSessionToken

class PlayCasinoGameCommandHandler(
    private val gameVariantRepository: ICasinoGameVariantRepository,
    private val aggregatorRepository: IAggregatorRepository,
    private val playerLimitPort: IPlayerLimitPort,
    private val openSessionUsecase: OpenCasinoSessionUsecase,
) : ICommandHandler<PlayCasinoGameCommand, PlayCasinoGameResult> {

    companion object {
        private const val BASE24_CHARS = "BCDFGHJKMPQRTVWXY2346789"
        private const val TOKEN_LENGTH = 32
    }

    override suspend fun handle(command: PlayCasinoGameCommand): Result<PlayCasinoGameResult> = runCatching {
        val gameVariant = domainRequireNotNull(
            gameVariantRepository.findActiveByGameIdentity(command.identity)
        ) { CasinoGameNotFoundException() }

        // The variant that was picked names its own aggregator, which is not always the provider's
        // — see CasinoSessionFactory.
        val aggregator = domainRequireNotNull(
            aggregatorRepository.findByIntegration(gameVariant.integration)
        ) { AggregatorNotFoundException() }

        if (command.maxSpinPlaceAmount != null) {
            playerLimitPort.saveMaxPlaceAmount(command.playerId, command.maxSpinPlaceAmount)
        }

        val session = CasinoSessionFactory.create(
            token = CasinoSessionToken(generateBase24Token()),
            playerId = command.playerId,
            gameVariant = gameVariant,
            aggregator = aggregator,
            currency = command.currency,
            locale = command.locale,
            platform = command.platform,
        )

        val result = openSessionUsecase(session, lobbyUrl = "").getOrThrow()

        // Token of the persisted session, not the provider's externalToken — our wallet webhooks
        // resolve by ours.
        PlayCasinoGameResult(launchUrl = result.launchUrl, sessionToken = result.session.token)
    }

    private fun generateBase24Token(): String = buildString(TOKEN_LENGTH) {
        repeat(TOKEN_LENGTH) {
            append(BASE24_CHARS.random())
        }
    }
}
