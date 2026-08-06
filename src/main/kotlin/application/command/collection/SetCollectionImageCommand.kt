package application.command.collection

import application.command.common.SetImageCommand
import domain.vo.Identity

data class SetCollectionImageCommand(
    override val identity: Identity,

    override val key: String,

    override val url: String,
) : SetImageCommand
