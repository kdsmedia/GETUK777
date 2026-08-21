package application.command.game

import application.ICommand
import domain.vo.Identity

data class SaveCasinoGameCommand(
    val identity: Identity,

    val name: String,

    val bonusBetEnable: Boolean,
    val bonusWageringEnable: Boolean,

    val tags: List<String>,

    val providerIdentity: Identity,

    val active: Boolean,

    val order: Int,
) : ICommand<Unit>
