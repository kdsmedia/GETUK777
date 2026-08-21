package infrastructure.aggregator.tech01sport

/**
 * 01.tech Sport (Betting System, sport.01.tech) integration config.
 *
 * The integration is reversed relative to casino aggregators: the Betting System calls the
 * Partner API that WE host, signing every request with the `betting-signature` header
 * (`v1 = HMAC-SHA256(secret, "<t>.<raw body>")`). Our only outbound calls are the freebet
 * endpoints, authenticated with a Bearer key.
 */
class Tech01SportConfig(config: Map<String, Any>) {

    /** Partner ID issued during integration setup — present in every inbound request and must
     *  match this value. */
    val partnerId = config["partnerId"]?.toString() ?: ""

    /** Active HMAC-SHA256 secrets for verifying the inbound `betting-signature` header. Several
     *  keys may be active at once (rotation); a request is valid if any key reproduces one of the
     *  received `v1` values. Accepts a JSON array (`secretKeys`) or a single `secretKey` string. */
    val secretKeys: List<String> = (config["secretKeys"] as? List<*>)?.map { it.toString() }
        ?: listOfNotNull(config["secretKey"]?.toString())

    /** Betting System REST base URL for our outbound calls (`/create-freebet`, `/get-freebets`). */
    val apiUrl = (config["apiUrl"]?.toString() ?: "").trimEnd('/')

    /** Partner API key for outbound Betting System calls — sent as `Authorization: Bearer <apiKey>`. */
    val apiKey = config["apiKey"]?.toString() ?: ""
}
