package infrastructure.handler.session

import application.IQueryHandler
import application.query.session.FindCasinoSessionBalanceQuery
import application.port.external.IWalletPort
import domain.model.PlayerBalance

class FindCasinoSessionBalanceHandler(
    private val walletAdapter: IWalletPort,
) : IQueryHandler<FindCasinoSessionBalanceQuery, PlayerBalance> {

    override suspend fun handle(query: FindCasinoSessionBalanceQuery): PlayerBalance =
        walletAdapter.findBalance(query.session.playerId, query.session.currency)
}
