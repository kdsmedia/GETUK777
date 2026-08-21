package infrastructure.aggregator.pateplay

import application.port.external.IFreespinPort
import application.port.external.ICasinoGamePort
import application.port.external.IJackpotStreamPort
import application.port.factory.AggregatorAdapterProvider
import domain.exception.conflict.JackpotStreamNotSupportedException
import infrastructure.aggregator.pateplay.adapter.PateplayFreespinAdapter
import infrastructure.aggregator.pateplay.adapter.PateplayGameAdapter

class PateplayAdapterProvider : AggregatorAdapterProvider {

    override val integration: String = INTEGRATION

    override fun createGameAdapter(config: Map<String, Any>): ICasinoGamePort =
        PateplayGameAdapter(PateplayConfig(config))

    override fun createFreespinAdapter(config: Map<String, Any>): IFreespinPort =
        PateplayFreespinAdapter(PateplayConfig(config))

    override fun createJackpotStreamAdapter(config: Map<String, Any>): IJackpotStreamPort =
        throw JackpotStreamNotSupportedException()

    companion object {
        const val INTEGRATION: String = "PATEPLAY"
    }
}
