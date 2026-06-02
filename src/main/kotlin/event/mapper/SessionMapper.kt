package event.mapper

import event.model.Session as SessionModel
import domain.model.Session as SessionDomain

fun SessionDomain.toModel(): SessionModel = SessionModel(
    id = id.toString(),
    playerId = playerId.value,
    gameIdentity = gameVariant.symbol.value,
    currency = currency.value,
    platform = platform.name,
)
