package application.query.game

import application.IQuery
import domain.vo.Page
import domain.vo.Pageable

/** RTP bucket relative to [domain.model.Game.DEFAULT_RTP]. */
enum class GameRtpType {
    HOT,
    COLD,
}

/**
 * Paged listing of ACTIVE games bucketed by RTP: [GameRtpType.HOT] = rtp above the
 * default ordered DESC, [GameRtpType.COLD] = rtp below the default ordered ASC.
 * Catalog position (`order`) is the secondary key (ASC) in both cases.
 */
data class FindAllActiveRtpGameQuery(
    val type: GameRtpType,

    val filter: GameFilter,

    val pageable: Pageable,
) : IQuery<Page<GameView>>
