package infrastructure.aggregator.skyline

/** The `action` field every Skyline message is dispatched on, in both directions. */
object SkylineAction {

    const val GAME_LIST = "game_list"

    const val GAME_LAUNCH = "game_launch"

    const val BONUS_AWARD = "bonus_award"

    const val BONUS_INFORMATION = "bonus_information"

    const val BONUS_CANCEL = "bonus_cancel"

    /** Inbound only — the provider asking us for the player's wallet. */
    const val GET_BALANCE = "get_balance"

    /** Inbound only — a bet, a win, or a refund of either. */
    const val UPDATE_BALANCE = "update_balance"
}
