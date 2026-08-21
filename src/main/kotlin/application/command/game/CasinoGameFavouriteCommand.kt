package application.command.game

import application.ICommand
import domain.vo.Identity
import domain.vo.PlayerId

data class AddCasinoGameFavouriteCommand(val identity: Identity, val playerId: PlayerId) : ICommand<Unit>

data class RemoveCasinoGameFavouriteCommand(val identity: Identity, val playerId: PlayerId) : ICommand<Unit>