package application.port.external

/**
 * Short-lived state an inbound aggregator webhook needs to stay safe under replay and retry.
 *
 * Both concerns are the same shape — a key that must be remembered for a bounded window — and
 * neither belongs in the database: they are ephemeral, high-churn, and worthless after expiry.
 */
interface IWebhookGuardPort {

    /**
     * Claims a single-use request nonce. Returns `false` if it was already claimed, which marks the
     * request as a replay. Signature schemes are only as strong as this check.
     */
    suspend fun claimNonce(key: String, ttlSeconds: Long): Boolean

    /**
     * Remembers that a provider transaction was rolled back before it ever arrived. Providers may
     * send a rollback first and the transaction afterwards; the late transaction must then be
     * refused rather than executed.
     */
    suspend fun markRolledBack(key: String, ttlSeconds: Long)

    suspend fun isRolledBack(key: String): Boolean
}
