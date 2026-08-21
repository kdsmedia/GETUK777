package infrastructure.aggregator.pragmatic

import application.port.external.IFreespinPort
import application.port.external.ICasinoGamePort
import application.port.external.IJackpotStreamPort
import application.port.factory.AggregatorAdapterProvider
import domain.exception.conflict.JackpotStreamNotSupportedException
import infrastructure.aggregator.pragmatic.adapter.PragmaticFreespinAdapter
import infrastructure.aggregator.pragmatic.adapter.PragmaticGameAdapter

class PragmaticAdapterProvider : AggregatorAdapterProvider {

    override val integration: String = INTEGRATION

    override fun createGameAdapter(config: Map<String, Any>): ICasinoGamePort =
        PragmaticGameAdapter(PragmaticConfig(config))

    override fun createFreespinAdapter(config: Map<String, Any>): IFreespinPort =
        PragmaticFreespinAdapter(PragmaticConfig(config))

    override fun createJackpotStreamAdapter(config: Map<String, Any>): IJackpotStreamPort =
        throw JackpotStreamNotSupportedException()

    companion object {
        const val INTEGRATION: String = "PRAGMATIC"
    }
}
