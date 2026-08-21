package infrastructure.aggregator.tech01sport.adapter

import application.port.external.ISportbookPort
import domain.model.SportbookSession
import infrastructure.aggregator.tech01sport.Tech01SportConfig

class Tech01SportbookAdapter(
    private val config: Tech01SportConfig,
) : ISportbookPort {

    override suspend fun open(session: SportbookSession): Map<String, String> = mapOf(
        "token" to session.token.value,
        "partnerId" to config.partnerId,
    )
}
