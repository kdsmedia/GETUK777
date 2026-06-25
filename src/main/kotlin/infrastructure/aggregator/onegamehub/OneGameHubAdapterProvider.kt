package infrastructure.aggregator.onegamehub

import application.port.external.IFreespinPort
import application.port.external.IGamePort
import application.port.external.ILotteryStreamPort
import application.port.factory.AggregatorAdapterProvider
import domain.exception.conflict.LotteryStreamNotSupportedException
import infrastructure.aggregator.onegamehub.adapter.OneGameHubFreespinAdapter
import infrastructure.aggregator.onegamehub.adapter.OneGameHubGameAdapter

class OneGameHubAdapterProvider : AggregatorAdapterProvider {

    override val integration: String = INTEGRATION

    override fun createGameAdapter(config: Map<String, Any>): IGamePort =
        OneGameHubGameAdapter(OneGameHubConfig(config))

    override fun createFreespinAdapter(config: Map<String, Any>): IFreespinPort =
        OneGameHubFreespinAdapter(OneGameHubConfig(config))

    override fun createLotteryStreamAdapter(config: Map<String, Any>): ILotteryStreamPort =
        throw LotteryStreamNotSupportedException()

    companion object {
        const val INTEGRATION: String = "ONEGAMEHUB"
    }
}
