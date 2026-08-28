package infrastructure.aggregator.tech01sport.adapter

import application.port.external.ISportbookPort
import domain.model.SportbookSession
import infrastructure.aggregator.tech01sport.Tech01SportConfig

class Tech01SportbookAdapter(
    private val config: Tech01SportConfig,
) : ISportbookPort {

    override suspend fun init(): Map<String, String> = mapOf(
        "partnerId" to config.partnerId,
        // Base URL of the Betting System backend the SDK frame talks to.
        "apiUrl" to config.apiUrl,
    )

    override suspend fun open(session: SportbookSession): Map<String, String> = mapOf(
        "token" to session.token.value,
        "partnerId" to config.partnerId,
        // Base URL of the Betting System backend the SDK frame talks to.
        "apiUrl" to config.apiUrl,
        // The session works in exactly this currency; the frontend passes it to the SDK init.
        "currency" to session.currency.value,
    )
}
