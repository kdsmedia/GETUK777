package application.port.factory

import application.port.external.IFreespinPort
import application.port.external.ICasinoGamePort
import application.port.external.IJackpotStreamPort
import application.port.external.ISportbookPort
import domain.model.Aggregator

/**
 * Resolves the correct game/freespin adapter for a given [Aggregator].
 *
 * Backed by a registry of [AggregatorAdapterProvider] instances — add a new aggregator
 * by creating a new provider and binding it in Koin. The factory itself never needs to change.
 */
interface IAggregatorFactory {

    fun createGameAdapter(aggregator: Aggregator): ICasinoGamePort

    fun createFreespinAdapter(aggregator: Aggregator): IFreespinPort

    fun createJackpotStreamAdapter(aggregator: Aggregator): IJackpotStreamPort

    fun createSportbookAdapter(aggregator: Aggregator): ISportbookPort
}
