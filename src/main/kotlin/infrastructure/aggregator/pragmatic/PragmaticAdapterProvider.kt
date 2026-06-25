package infrastructure.aggregator.pragmatic

import application.port.external.IFreespinPort
import application.port.external.IGamePort
import application.port.external.ILotteryStreamPort
import application.port.factory.AggregatorAdapterProvider
import domain.exception.conflict.LotteryStreamNotSupportedException
import infrastructure.aggregator.pragmatic.adapter.PragmaticFreespinAdapter
import infrastructure.aggregator.pragmatic.adapter.PragmaticGameAdapter

class PragmaticAdapterProvider : AggregatorAdapterProvider {

    override val integration: String = INTEGRATION

    override fun createGameAdapter(config: Map<String, Any>): IGamePort =
        PragmaticGameAdapter(PragmaticConfig(config))

    override fun createFreespinAdapter(config: Map<String, Any>): IFreespinPort =
        PragmaticFreespinAdapter(PragmaticConfig(config))

    override fun createLotteryStreamAdapter(config: Map<String, Any>): ILotteryStreamPort =
        throw LotteryStreamNotSupportedException()

    companion object {
        const val INTEGRATION: String = "PRAGMATIC"
    }
}
