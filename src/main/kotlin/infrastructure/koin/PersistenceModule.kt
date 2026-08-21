package infrastructure.koin

import domain.repository.IAggregatorRepository
import domain.repository.IBetRepository
import domain.repository.ICollectionRepository
import domain.repository.ISportbookSessionRepository
import domain.repository.IFreespinRepository
import domain.repository.ICasinoGameRepository
import domain.repository.ICasinoGameVariantRepository
import domain.repository.ICasinoProviderRepository
import domain.repository.ICasinoRoundRepository
import domain.repository.ICasinoSessionRepository
import domain.repository.ISpinRepository
import infrastructure.persistence.repository.AggregatorRepositoryImpl
import infrastructure.persistence.repository.BetRepositoryImpl
import infrastructure.persistence.repository.CollectionRepositoryImpl
import infrastructure.persistence.repository.SportbookSessionRepositoryImpl
import infrastructure.persistence.repository.FreespinRepositoryImpl
import infrastructure.persistence.repository.CasinoGameRepositoryImpl
import infrastructure.persistence.repository.CasinoGameVariantRepositoryImpl
import infrastructure.persistence.repository.CasinoProviderRepositoryImpl
import infrastructure.persistence.repository.CasinoRoundRepositoryImpl
import infrastructure.persistence.repository.CasinoSessionRepositoryImpl
import infrastructure.persistence.repository.SpinRepositoryImpl
import org.koin.dsl.module

val persistenceModule = module {
    single<ICasinoSessionRepository> { CasinoSessionRepositoryImpl() }
    single<ICasinoRoundRepository> { CasinoRoundRepositoryImpl() }
    single<ISpinRepository> { SpinRepositoryImpl() }
    single<IFreespinRepository> { FreespinRepositoryImpl() }
    single<ICasinoGameRepository> { CasinoGameRepositoryImpl() }
    single<ICasinoGameVariantRepository> { CasinoGameVariantRepositoryImpl() }
    single<ICasinoProviderRepository> { CasinoProviderRepositoryImpl() }
    single<ICollectionRepository> { CollectionRepositoryImpl() }
    single<IAggregatorRepository> { AggregatorRepositoryImpl() }
    single<IBetRepository> { BetRepositoryImpl() }
    single<ISportbookSessionRepository> { SportbookSessionRepositoryImpl() }
}
