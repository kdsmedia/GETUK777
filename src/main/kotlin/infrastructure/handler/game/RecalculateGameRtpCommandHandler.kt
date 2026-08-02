package infrastructure.handler.game

import application.ICommandHandler
import application.command.game.RecalculateGameRtpCommand
import application.usecase.RecalculateGameRtpUsecase

class RecalculateGameRtpCommandHandler(
    private val recalculateGameRtpUsecase: RecalculateGameRtpUsecase,
) : ICommandHandler<RecalculateGameRtpCommand, Int> {

    override suspend fun handle(command: RecalculateGameRtpCommand): Result<Int> =
        recalculateGameRtpUsecase(command.since)
}
