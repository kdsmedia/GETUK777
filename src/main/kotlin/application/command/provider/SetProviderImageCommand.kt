package application.command.provider

import application.command.common.SetImageCommand
import domain.vo.Identity

data class SetProviderImageCommand(
    override val identity: Identity,

    override val key: String,

    override val url: String,
) : SetImageCommand
