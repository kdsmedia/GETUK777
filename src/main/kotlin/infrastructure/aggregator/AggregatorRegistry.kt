package infrastructure.aggregator

import application.port.external.IFreespinPort
import application.port.external.ICasinoGamePort
import application.port.external.IJackpotStreamPort
import application.port.external.ISportbookPort
import application.port.factory.AggregatorAdapterProvider
import application.port.factory.IAggregatorFactory
import domain.exception.badrequest.AggregatorNotSupportedException
import domain.model.Aggregator

/**
 * Resolves aggregator adapters by looking up the [AggregatorAdapterProvider] whose
 * [AggregatorAdapterProvider.integration] matches [Aggregator.integration].
 *
 * Zero-touch extensibility: Koin's `getAll<AggregatorAdapterProvider>()` surfaces every
 * bound provider at boot. Adding a new aggregator never requires editing this class.
 */
class AggregatorRegistry(
    providers: List<AggregatorAdapterProvider>,
) : IAggregatorFactory {

    private val providersByIntegration: Map<String, AggregatorAdapterProvider> =
        providers.associateBy(AggregatorAdapterProvider::integration)

    override fun createGameAdapter(aggregator: Aggregator): ICasinoGamePort =
        resolve(aggregator).createGameAdapter(aggregator.config)

    override fun createFreespinAdapter(aggregator: Aggregator): IFreespinPort =
        resolve(aggregator).createFreespinAdapter(aggregator.config)

    override fun createJackpotStreamAdapter(aggregator: Aggregator): IJackpotStreamPort =
        resolve(aggregator).createJackpotStreamAdapter(aggregator.config)

    override fun createSportbookAdapter(aggregator: Aggregator): ISportbookPort =
        resolve(aggregator).createSportbookAdapter(aggregator.config)

    private fun resolve(aggregator: Aggregator): AggregatorAdapterProvider =
        providersByIntegration[aggregator.integration]
            ?: throw AggregatorNotSupportedException(aggregator.integration)
}
