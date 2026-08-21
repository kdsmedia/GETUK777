package domain.model

import domain.service.CasinoRoundFactory
import domain.vo.Currency
import domain.vo.ExternalCasinoRoundId
import domain.vo.FreespinId
import domain.vo.Locale
import domain.vo.PlayerId
import domain.vo.CasinoSessionToken
import kotlinx.serialization.Serializable

@Serializable
data class CasinoSession(
    val id: Long = Long.MIN_VALUE,

    val gameVariant: CasinoGameVariant,

    val playerId: PlayerId,

    val token: CasinoSessionToken,

    val externalToken: String?,

    val currency: Currency,

    val locale: Locale,

    val platform: Platform,
) {
    /**
     * Opens a new [CasinoRound] against this session. Keeps round creation anchored to its
     * parent aggregate so usecases don't have to know about [CasinoRoundFactory].
     */
    fun openRound(externalId: ExternalCasinoRoundId, freespinId: FreespinId? = null): CasinoRound =
        CasinoRoundFactory.open(session = this, externalId = externalId, freespinId = freespinId)
}
