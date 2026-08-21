package domain.service

import domain.model.CasinoRound
import domain.model.CasinoSession
import domain.vo.ExternalCasinoRoundId
import domain.vo.FreespinId

object CasinoRoundFactory {

    fun open(session: CasinoSession, externalId: ExternalCasinoRoundId, freespinId: FreespinId?): CasinoRound =
        CasinoRound(
            externalId = externalId,
            freespinId = freespinId,
            session = session,
            gameVariant = session.gameVariant,
        )
}
