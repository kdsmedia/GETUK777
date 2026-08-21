package application.command.provider

import application.ICommand
import domain.vo.Country
import domain.vo.Identity

data class SaveCasinoProviderCommand(
    val identity: Identity,

    val name: String,

    val order: Int,

    val active: Boolean,

    val aggregatorIdentity: Identity,

    val blockedCountry: List<Country> = emptyList(),

    val tags: List<String> = emptyList(),
) : ICommand<Unit>
