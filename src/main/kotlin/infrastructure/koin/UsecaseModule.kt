package infrastructure.koin

import application.usecase.DecreasePlayerLimitUsecase
import application.usecase.FinishCasinoRoundUsecase
import application.usecase.JackpotBroadcaster
import application.usecase.OpenCasinoSessionUsecase
import application.usecase.OpenSportbookUsecase
import application.usecase.ProcessBetUsecase
import application.usecase.ProcessSpinUsecase
import application.usecase.ProcessWheelUsecase
import application.usecase.RecalculateCasinoGameRtpUsecase
import application.usecase.SyncAggregatorUsecase
import org.koin.dsl.module

val usecaseModule = module {
    single {
        ProcessSpinUsecase(
            spinRepository = get(),
            eventPublisher = get(),
            walletPort = get(),
            playerLimitPort = get(),
        )
    }
    single {
        OpenCasinoSessionUsecase(
            aggregatorFactory = get(),
            aggregatorRepository = get(),
            sessionRepository = get(),
            freespinRepository = get(),
            eventPublisher = get(),
        )
    }
    single {
        JackpotBroadcaster(
            aggregatorRepository = get(),
            aggregatorFactory = get(),
        )
    }
    single {
        FinishCasinoRoundUsecase(
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
        RecalculateCasinoGameRtpUsecase(
            spinRepository = get(),
            gameRepository = get(),
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
    single {
        OpenSportbookUsecase(
            aggregatorRepository = get(),
            sessionRepository = get(),
            aggregatorFactory = get(),
            eventPublisher = get(),
        )
    }
    single {
        ProcessBetUsecase(
            betRepository = get(),
            walletPort = get(),
            eventPublisher = get(),
        )
    }
    single {
        ProcessWheelUsecase(
            walletPort = get(),
            guardPort = get(),
        )
    }
}
