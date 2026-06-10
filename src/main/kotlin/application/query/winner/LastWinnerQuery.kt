package application.query.winner

import application.IQuery
import domain.model.Game
import domain.model.GameVariant
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.Identity
import domain.vo.Page
import domain.vo.Pageable
import domain.vo.PlayerId
import kotlinx.datetime.Instant

data class LastWin(
    val game: Game,
    val variant: GameVariant?,
    val amount: Amount,
    val currency: Currency,
    val playerId: PlayerId,
    val date: Instant,
)

data class LastWinnerQuery(
    val gameIdentity: Identity? = null,

    val minAmount: Amount? = null,
    val maxAmount: Amount? = null,

    val currency: Currency? = null,

    val playerId: PlayerId? = null,

    val fromDate: Instant? = null,
    val toDate: Instant? = null,

    val pageable: Pageable
) : IQuery<Page<LastWin>>
