package event.mapper

import event.model.Round as RoundModel
import domain.model.Round as RoundDomain

fun RoundDomain.toModel(): RoundModel {
    val session = this.session
    return RoundModel(
        id = externalId.value,
        playerId = session.playerId.value,
        gameIdentity = session.gameVariant.symbol.value,
        freespinId = freespinId?.value,
        finished = isFinished,
    )
}
