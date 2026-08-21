package application.query.round

import application.IQuery
import domain.model.CasinoRound
import domain.vo.Amount
import java.util.Optional

/**
 * Read-side view of a [CasinoRound] enriched with aggregated spin totals.
 * Reused by [FindCasinoRoundQuery] (single) and [FindAllCasinoRoundQuery] (page).
 */
data class CasinoRoundView(
    val round: CasinoRound,

    val totalPlace: Amount,

    val totalSettle: Amount,
)

data class FindCasinoRoundQuery(
    val id: Long,
) : IQuery<Optional<CasinoRoundView>>
