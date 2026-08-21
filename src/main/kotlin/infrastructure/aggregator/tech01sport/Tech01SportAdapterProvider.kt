package infrastructure.aggregator.tech01sport

import application.port.external.IFreespinPort
import application.port.external.ICasinoGamePort
import application.port.external.IJackpotStreamPort
import application.port.external.ISportbookPort
import application.port.factory.AggregatorAdapterProvider
import domain.exception.badrequest.CasinoNotSupportedException
import infrastructure.aggregator.tech01sport.adapter.Tech01SportbookAdapter

class Tech01SportAdapterProvider : AggregatorAdapterProvider {

    override val integration: String = INTEGRATION

    override fun createGameAdapter(config: Map<String, Any>): ICasinoGamePort =
        throw CasinoNotSupportedException(INTEGRATION)

    override fun createFreespinAdapter(config: Map<String, Any>): IFreespinPort =
        throw CasinoNotSupportedException(INTEGRATION)

    override fun createJackpotStreamAdapter(config: Map<String, Any>): IJackpotStreamPort =
        throw CasinoNotSupportedException(INTEGRATION)

    override fun createSportbookAdapter(config: Map<String, Any>): ISportbookPort =
        Tech01SportbookAdapter(Tech01SportConfig(config))

    companion object {
        const val INTEGRATION: String = "01TECHSPORT"
    }
}
