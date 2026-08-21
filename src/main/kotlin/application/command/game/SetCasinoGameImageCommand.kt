package application.command.game

import application.command.common.SetImageCommand
import domain.vo.Identity

data class SetCasinoGameImageCommand(
    override val identity: Identity,

    override val key: String,

    override val url: String,
) : SetImageCommand
