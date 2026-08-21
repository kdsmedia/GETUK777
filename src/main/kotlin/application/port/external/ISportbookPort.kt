package application.port.external

import domain.model.SportbookSession

interface ISportbookPort {

    /**
     * Opens the sportbook for the session and returns the aggregator-specific launch data
     * the frontend needs to initialize the provider's SDK (e.g. the session's public token).
     */
    suspend fun open(session: SportbookSession): Map<String, String>
}
