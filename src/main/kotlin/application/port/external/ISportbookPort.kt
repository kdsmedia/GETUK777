package application.port.external

import domain.model.SportbookSession

interface ISportbookPort {

    /**
     * Anonymous SDK init data — what the frontend needs to boot the provider's SDK without a
     * player session (e.g. partnerId, apiUrl). A guest browses the line with exactly this.
     */
    suspend fun init(): Map<String, String>

    /**
     * Opens the sportbook for the session and returns the aggregator-specific launch data
     * the frontend needs to initialize the provider's SDK (e.g. the session's public token).
     */
    suspend fun open(session: SportbookSession): Map<String, String>
}
