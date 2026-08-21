package application.command.provider

import application.command.common.SetImageCommand
import domain.vo.Identity

data class SetCasinoProviderImageCommand(
    override val identity: Identity,

    override val key: String,

    override val url: String,
) : SetImageCommand
