package infrastructure.aggregator.tongame

import application.port.external.IFreespinPort
import application.port.external.IGamePort
import application.port.factory.AggregatorAdapterProvider
import infrastructure.aggregator.tongame.adapter.TongameFreespinAdapter
import infrastructure.aggregator.tongame.adapter.TongameGameAdapter

class TongameAdapterProvider : AggregatorAdapterProvider {

    override val integration: String = INTEGRATION

    override fun createGameAdapter(config: Map<String, Any>): IGamePort =
        TongameGameAdapter(TongameConfig(config))

    override fun createFreespinAdapter(config: Map<String, Any>): IFreespinPort =
        TongameFreespinAdapter()

    companion object {
        const val INTEGRATION: String = "TONGAME"
    }
}
