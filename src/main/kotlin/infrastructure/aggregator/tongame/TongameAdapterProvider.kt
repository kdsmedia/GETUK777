package infrastructure.aggregator.tongame

import application.port.external.IFreespinPort
import application.port.external.ICasinoGamePort
import application.port.external.IJackpotStreamPort
import application.port.factory.AggregatorAdapterProvider
import infrastructure.aggregator.tongame.adapter.TongameFreespinAdapter
import infrastructure.aggregator.tongame.adapter.TongameGameAdapter
import infrastructure.aggregator.tongame.stream.TongameJackpotSocketClient

class TongameAdapterProvider : AggregatorAdapterProvider {

    override val integration: String = INTEGRATION

    override fun createGameAdapter(config: Map<String, Any>): ICasinoGamePort =
        TongameGameAdapter(TongameConfig(config))

    override fun createFreespinAdapter(config: Map<String, Any>): IFreespinPort =
        TongameFreespinAdapter()

    override fun createJackpotStreamAdapter(config: Map<String, Any>): IJackpotStreamPort =
        TongameJackpotSocketClient(TongameConfig(config))

    companion object {
        const val INTEGRATION: String = "TONGAME"
    }
}
