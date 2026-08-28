package api.grpc.service

import api.grpc.config.handleGrpcCall
import application.Bus
import application.query.sportbook.InitSportbookQuery
import com.nekgamebling.game.v1.InitSportbookQueryKt
import com.nekgamebling.game.v1.OpenSportbookCommandKt
import com.nekgamebling.game.v1.SportbookServiceGrpcKt
import domain.vo.Currency
import domain.vo.PlayerId
import application.command.sportbook.OpenSportbookCommand as OpenSportbookCqrs
import com.nekgamebling.game.v1.InitSportbookQuery as InitSportbookProto
import com.nekgamebling.game.v1.OpenSportbookCommand as OpenSportbookProto

class SportbookGrpcService(
    private val bus: Bus,
) : SportbookServiceGrpcKt.SportbookServiceCoroutineImplBase() {

    override suspend fun open(request: OpenSportbookProto): OpenSportbookProto.Result = handleGrpcCall {
        val session = bus(
            OpenSportbookCqrs(
                playerId = PlayerId(request.playerId),
                currency = Currency(request.currency),
            )
        )

        OpenSportbookCommandKt.result {
            integration = session.aggregator.integration
            data.putAll(session.data)
        }
    }

    override suspend fun init(request: InitSportbookProto): InitSportbookProto.Result = handleGrpcCall {
        val init = bus(InitSportbookQuery)

        InitSportbookQueryKt.result {
            integration = init.integration
            data.putAll(init.data)
        }
    }
}
