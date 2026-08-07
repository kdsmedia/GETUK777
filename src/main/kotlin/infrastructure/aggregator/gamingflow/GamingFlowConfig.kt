package infrastructure.aggregator.gamingflow

/**
 * GamingFlow (Operator API v1 / Seamless API v2) integration config.
 *
 * The provider exposes a single JSON-RPC 2.0 endpoint. Two authentication modes exist —
 * TLS client certificates (`https://api.gaming-flow.org/v1/`) and HMAC signatures
 * (`https://customer.gaming-flow.org/v1/signed/`). We use the signature endpoint: the
 * operator sits behind Cloudflare, which cannot forward client certificates.
 */
class GamingFlowConfig(config: Map<String, Any>) {

    /** JSON-RPC endpoint, e.g. `https://customer.gaming-flow.org/v1/signed/`. */
    val apiUrl = config["apiUrl"]?.toString() ?: ""

    /** Our casino identifier at the provider. Travels as `X-Subject: casino:<casinoId>` outbound
     *  and comes back as `callerId` on every Seamless API call. */
    val casinoId = config["casinoId"]?.toString() ?: ""

    /** Signature key identifier — the `<keyId>=` prefix of the `X-Signature` header value. */
    val keyId = config["keyId"]?.toString() ?: ""

    /** Signature secret. HMAC-SHA256 key for both signing outbound and verifying inbound requests. */
    val keyValue = config["keyValue"]?.toString() ?: ""

    /** Game session domain, e.g. `gamix.party`. Sent as `BaseHost` on session creation; the
     *  playable URL is `https://<SessionId>.<baseHost>/`. Swap for a proxy host once one exists. */
    val baseHost = (config["baseHost"]?.toString() ?: "").trim('/')

    /** Optional CDN host the game pulls its static files from — sent as `StaticHost`. */
    val staticHost = config["staticHost"]?.toString() ?: ""

    /** Prefix for provider-side bank group ids. A bank group is a set of players sharing one
     *  currency, so ids are minted per currency: `<bankGroupPrefix>_<CURRENCY>`. */
    val bankGroupPrefix = config["bankGroupPrefix"]?.toString() ?: ""

    /** Session creation behaviour. `Restore` re-hydrates the previous session state when one
     *  matches, otherwise falls back to `Create`. */
    val restorePolicy = config["restorePolicy"]?.toString()?.ifBlank { null } ?: DEFAULT_RESTORE_POLICY

    /** Demo session starting balance in minor units. */
    val demoStartBalance = (config["demoStartBalance"] as? Number)?.toLong()
        ?: config["demoStartBalance"]?.toString()?.toLongOrNull()
        ?: DEFAULT_DEMO_START_BALANCE

    /** Value of the `X-Subject` header — the provider rejects requests whose subject does not
     *  match the key that signed them. */
    val subject: String = "$SUBJECT_PREFIX$casinoId"

    /**
     * Provider-side bank group id for [currency]. Currency is immutable once a bank group exists,
     * so a group can never be shared across currencies.
     */
    fun bankGroupId(currency: String): String =
        if (bankGroupPrefix.isBlank()) currency.uppercase()
        else "${bankGroupPrefix}_${currency.uppercase()}"

    /**
     * Provider-side player id. A player is bound to one bank group — and therefore one currency —
     * for life, so a multi-currency player maps to one provider player per currency.
     * Inverse of [playerIdOf].
     */
    fun playerName(playerId: String, currency: String): String =
        "$playerId$PLAYER_CURRENCY_SEPARATOR${currency.uppercase()}"

    /** Recovers our `PlayerId` from a provider-side player name produced by [playerName]. */
    fun playerIdOf(playerName: String): String =
        playerName.substringBeforeLast(PLAYER_CURRENCY_SEPARATOR)

    private companion object {
        const val SUBJECT_PREFIX = "casino:"

        const val PLAYER_CURRENCY_SEPARATOR = "_"

        const val DEFAULT_RESTORE_POLICY = "Restore"

        const val DEFAULT_DEMO_START_BALANCE = 10_000L
    }
}
