package infrastructure.handler.game

import application.IQueryHandler
import application.query.game.GetCasinoGameDemoUrlQuery
import application.port.factory.IAggregatorFactory
import domain.repository.ICasinoGameVariantRepository
import domain.exception.domainRequireNotNull
import domain.exception.notfound.CasinoGameNotFoundException

class GetCasinoGameDemoUrlQueryHandler(
    private val gameVariantRepository: ICasinoGameVariantRepository,
    private val aggregatorFactory: IAggregatorFactory,
) : IQueryHandler<GetCasinoGameDemoUrlQuery, String> {

    override suspend fun handle(query: GetCasinoGameDemoUrlQuery): String {
        val gameVariant = domainRequireNotNull(
            gameVariantRepository.findActiveByGameIdentity(query.identity)
        ) { CasinoGameNotFoundException() }

        val gameAdapter = aggregatorFactory.createGameAdapter(gameVariant.game.provider.aggregator)

        return gameAdapter.getDemoUrl(
            gameSymbol = gameVariant.symbol.value,
            locale = query.locale,
            platform = query.platform,
            currency = query.currency,
            lobbyUrl = query.lobbyUrl,
        )
    }
}
