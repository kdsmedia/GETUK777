package event.mapper

import event.model.SpinType
import event.model.Spin as SpinModel
import domain.model.Spin as SpinDomain
import domain.model.SpinType as SpinTypeDomain

private fun SpinTypeDomain.toModel(): SpinType = when (this) {
    SpinTypeDomain.PLACE -> SpinType.Place
    SpinTypeDomain.SETTLE -> SpinType.Settle
    SpinTypeDomain.ROLLBACK -> SpinType.Rollback
}

fun SpinDomain.toModel(): SpinModel {
    val session = round.session
    return SpinModel(
        id = externalId.value,
        playerId = session.playerId.value,
        type = type.toModel(),
        amount = amount.value,
        currency = session.currency.value,
        gameIdentity = session.gameVariant.symbol.value,
        freespinId = round.freespinId?.value,
    )
}
