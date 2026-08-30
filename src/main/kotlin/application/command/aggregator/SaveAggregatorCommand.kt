package application.command.aggregator

import application.ICommand
import domain.model.AggregatorType
import domain.vo.Identity

data class SaveAggregatorCommand(
    val identity: Identity,
    val config: Map<String, Any>,
    val isProxy: Boolean,
    val active: Boolean,
    val integration: String,
    val type: AggregatorType,
) : ICommand<Unit>