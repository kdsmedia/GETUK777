package domain.model

import domain.util.ext.InstantExt
import domain.vo.Currency
import domain.vo.PlayerId
import domain.vo.SportbookSessionToken
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Sportbook launch context. `aggregator.integration` tells the frontend which provider SDK
 * to load; `data` carries the aggregator-specific init payload (e.g. the public token).
 * [token] is our public token; [externalToken] is the private token minted when the provider
 * exchanges the public one (e.g. 01.tech `create-private-token`) — null until then.
 */
@Serializable
data class SportbookSession(
    val id: Long = Long.MIN_VALUE,

    val token: SportbookSessionToken,

    val externalToken: String? = null,

    val playerId: PlayerId,

    val currency: Currency,

    val aggregator: Aggregator,

    val data: Map<String, String>,

    val createdAt: Instant = InstantExt.now(),
)
