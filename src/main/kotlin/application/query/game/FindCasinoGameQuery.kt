package application.query.game

import application.IQuery
import domain.model.CasinoGame
import domain.model.CasinoGameVariant
import domain.vo.Identity
import java.util.Optional

/**
 * Read-side projection of a [CasinoGame] together with its currently-active [CasinoGameVariant]
 * (if any). Reused by every game-listing query (`FindCasinoGameQuery`, `FindAllCasinoGameQuery`,
 * `BatchCasinoGameQuery`, `FindAllCasinoGamePlayerFavoriteQuery`). The variant is optional because
 * a game without an active variant is still a valid catalog row.
 */
data class CasinoGameView(
    val game: CasinoGame,

    val variant: CasinoGameVariant?,
)

data class FindCasinoGameQuery(
    val identity: Identity,
) : IQuery<Optional<CasinoGameView>>
