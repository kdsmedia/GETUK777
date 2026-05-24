package infrastructure.handler.session

import application.IQueryHandler
import application.query.session.FindSessionBalanceQuery
import application.port.external.IWalletPort
import domain.model.PlayerBalance

class FindSessionBalanceHandler(
    private val walletAdapter: IWalletPort,
) : IQueryHandler<FindSessionBalanceQuery, PlayerBalance> {

    override suspend fun handle(query: FindSessionBalanceQuery): PlayerBalance =
        walletAdapter.findBalance(query.session.playerId, query.session.currency)
}
