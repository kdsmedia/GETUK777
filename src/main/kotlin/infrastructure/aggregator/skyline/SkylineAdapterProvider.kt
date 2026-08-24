package infrastructure.aggregator.skyline

import application.port.external.IFreespinPort
import application.port.external.ICasinoGamePort
import application.port.external.IJackpotStreamPort
import application.port.factory.AggregatorAdapterProvider
import domain.exception.conflict.JackpotStreamNotSupportedException
import infrastructure.aggregator.skyline.adapter.SkylineFreespinAdapter
import infrastructure.aggregator.skyline.adapter.SkylineGameAdapter

class SkylineAdapterProvider : AggregatorAdapterProvider {

    override val integration: String = INTEGRATION

    override fun createGameAdapter(config: Map<String, Any>): ICasinoGamePort =
        SkylineGameAdapter(SkylineConfig(config))

    override fun createFreespinAdapter(config: Map<String, Any>): IFreespinPort =
        SkylineFreespinAdapter(SkylineConfig(config))

    /** The vendor publishes no jackpot feed. */
    override fun createJackpotStreamAdapter(config: Map<String, Any>): IJackpotStreamPort =
        throw JackpotStreamNotSupportedException()

    companion object {
        const val INTEGRATION: String = "SKYLINE"
    }
}
