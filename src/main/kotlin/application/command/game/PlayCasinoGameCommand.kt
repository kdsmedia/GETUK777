package application.command.game

import application.ICommand
import domain.model.Platform
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.Identity
import domain.vo.Locale
import domain.vo.PlayerId
import domain.vo.CasinoSessionToken

data class PlayCasinoGameResult(
    val launchUrl: String,

    val sessionToken: CasinoSessionToken,
)

data class PlayCasinoGameCommand(
    val identity: Identity,

    val playerId: PlayerId,

    val locale: Locale,

    val platform: Platform,

    val currency: Currency,

    val maxSpinPlaceAmount: Amount?,
) : ICommand<PlayCasinoGameResult>
