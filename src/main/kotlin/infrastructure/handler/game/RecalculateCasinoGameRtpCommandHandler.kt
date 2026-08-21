package infrastructure.handler.game

import application.ICommandHandler
import application.command.game.RecalculateCasinoGameRtpCommand
import application.usecase.RecalculateCasinoGameRtpUsecase

class RecalculateCasinoGameRtpCommandHandler(
    private val recalculateCasinoGameRtpUsecase: RecalculateCasinoGameRtpUsecase,
) : ICommandHandler<RecalculateCasinoGameRtpCommand, Int> {

    override suspend fun handle(command: RecalculateCasinoGameRtpCommand): Result<Int> =
        recalculateCasinoGameRtpUsecase(command.since)
}
