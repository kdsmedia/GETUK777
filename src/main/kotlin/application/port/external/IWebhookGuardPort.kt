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

    /**
     * Runs [block] with exclusive access to [key], waiting for a holder to finish.
     *
     * A provider transaction that both takes a bet and pays a win moves the wallet twice, and
     * without this the two legs are separately observable: a concurrent transaction reads the
     * balance between them and reports one that reflects half of somebody else's bet. The lock
     * makes one transaction atomic as far as the rest of them can tell.
     *
     * Throws if the wait is exhausted — the caller has done nothing at that point, so answering
     * an error is safe and no money is left in doubt.
     */
    suspend fun <T> withLock(key: String, block: suspend () -> T): T
}
