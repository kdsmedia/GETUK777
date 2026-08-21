package infrastructure.persistence.table

import org.jetbrains.exposed.dao.id.LongIdTable

object CasinoGameFavouriteTable : LongIdTable("casino_game_favourites") {
    val game = reference("game_id", CasinoGameTable)
    val playerId = varchar("player_id", 255)

    init {
        uniqueIndex(playerId, game)
    }
}
