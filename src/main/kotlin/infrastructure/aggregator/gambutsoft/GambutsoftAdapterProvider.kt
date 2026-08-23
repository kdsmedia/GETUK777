package infrastructure.aggregator.gambutsoft

import application.port.external.IFreespinPort
import application.port.external.ICasinoGamePort
import application.port.external.IJackpotStreamPort
import application.port.factory.AggregatorAdapterProvider
import domain.exception.conflict.JackpotStreamNotSupportedException
import infrastructure.aggregator.gambutsoft.adapter.GambutsoftFreespinAdapter
import infrastructure.aggregator.gambutsoft.adapter.GambutsoftGameAdapter

class GambutsoftAdapterProvider : AggregatorAdapterProvider {

    override val integration: String = INTEGRATION

    override fun createGameAdapter(config: Map<String, Any>): ICasinoGamePort =
        GambutsoftGameAdapter(GambutsoftConfig(config))

    override fun createFreespinAdapter(config: Map<String, Any>): IFreespinPort =
        GambutsoftFreespinAdapter()

    /** The vendor publishes no jackpot feed. */
    override fun createJackpotStreamAdapter(config: Map<String, Any>): IJackpotStreamPort =
        throw JackpotStreamNotSupportedException()

    companion object {
        const val INTEGRATION: String = "GAMBUTSOFT"
    }
}
