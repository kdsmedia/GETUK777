package infrastructure.aggregator.gamingflow

import application.port.external.IFreespinPort
import application.port.external.IGamePort
import application.port.external.IJackpotStreamPort
import application.port.factory.AggregatorAdapterProvider
import domain.exception.conflict.JackpotStreamNotSupportedException
import infrastructure.aggregator.gamingflow.adapter.GamingFlowFreespinAdapter
import infrastructure.aggregator.gamingflow.adapter.GamingFlowGameAdapter

class GamingFlowAdapterProvider : AggregatorAdapterProvider {

    override val integration: String = INTEGRATION

    override fun createGameAdapter(config: Map<String, Any>): IGamePort =
        GamingFlowGameAdapter(GamingFlowConfig(config))

    override fun createFreespinAdapter(config: Map<String, Any>): IFreespinPort =
        GamingFlowFreespinAdapter(GamingFlowConfig(config))

    /**
     * The provider does publish a jackpot feed, but it is an SSE stream of raw slot counters
     * (`slots_values: [int]`) — it carries no draw identity, currency, window or prize pool, so it
     * cannot honestly fill a [application.port.external.JackpotState].
     */
    override fun createJackpotStreamAdapter(config: Map<String, Any>): IJackpotStreamPort =
        throw JackpotStreamNotSupportedException()

    companion object {
        const val INTEGRATION: String = "GAMINGFLOW"
    }
}
