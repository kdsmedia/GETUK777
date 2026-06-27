package infrastructure.koin

import application.usecase.DecreasePlayerLimitUsecase
import application.usecase.FinishRoundUsecase
import application.usecase.JackpotBroadcaster
import application.usecase.OpenSessionUsecase
import application.usecase.ProcessSpinUsecase
import application.usecase.SyncAggregatorUsecase
import org.koin.dsl.module

val usecaseModule = module {
    single {
        ProcessSpinUsecase(
            spinRepository = get(),
            eventPublisher = get(),
            walletPort = get(),
            playerLimitPort = get(),
            backgroundTaskPort = get(),
        )
    }
    single {
        OpenSessionUsecase(
            aggregatorFactory = get(),
            sessionRepository = get(),
            eventPublisher = get(),
        )
    }
    single {
        JackpotBroadcaster(
            gameVariantRepository = get(),
            aggregatorFactory = get(),
        )
    }
    single {
        FinishRoundUsecase(
            roundRepository = get(),
            eventPublisher = get(),
        )
    }
    single {
        DecreasePlayerLimitUsecase(
            playerLimitPort = get(),
        )
    }
    single {
        SyncAggregatorUsecase(
            aggregatorFactory = get(),
            gameRepository = get(),
            gameVariantRepository = get(),
            providerRepository = get(),
        )
    }
}
