package infrastructure.aggregator.onegamehub

import application.port.external.IFreespinPort
import application.port.external.ICasinoGamePort
import application.port.external.IJackpotStreamPort
import application.port.factory.AggregatorAdapterProvider
import domain.exception.conflict.JackpotStreamNotSupportedException
import infrastructure.aggregator.onegamehub.adapter.OneGameHubFreespinAdapter
import infrastructure.aggregator.onegamehub.adapter.OneGameHubGameAdapter

class OneGameHubAdapterProvider : AggregatorAdapterProvider {

    override val integration: String = INTEGRATION

    override fun createGameAdapter(config: Map<String, Any>): ICasinoGamePort =
        OneGameHubGameAdapter(OneGameHubConfig(config))

    override fun createFreespinAdapter(config: Map<String, Any>): IFreespinPort =
        OneGameHubFreespinAdapter(OneGameHubConfig(config))

    override fun createJackpotStreamAdapter(config: Map<String, Any>): IJackpotStreamPort =
        throw JackpotStreamNotSupportedException()

    companion object {
        const val INTEGRATION: String = "ONEGAMEHUB"
    }
}
