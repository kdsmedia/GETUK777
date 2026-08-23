package infrastructure.aggregator.gambutsoft

/**
 * Gambutsoft (Gamble Hub, games-hub.net) integration config.
 *
 * The vendor splits its API across two hosts: the office API issues the JWT the catalogue needs,
 * while the client API opens game sessions and is authenticated by request signature alone. Both
 * directions — our `openGame` call and their three wallet callbacks — are signed with the same
 * [secretKey]: `X-Signature = hex HMAC-SHA256(secretKey, raw JSON body)`.
 */
class GambutsoftConfig(config: Map<String, Any>) {

    /** Office API base, e.g. `https://office-api-dev.gamble-hub.net`. Serves `/auth/login` and the catalogue. */
    val officeApiUrl = (config["officeApiUrl"]?.toString() ?: "").trimEnd('/')

    /** Client API base, e.g. `https://client-api-dev.gamble-hub.net`. Serves `POST /games/openGame`. */
    val clientApiUrl = (config["clientApiUrl"]?.toString() ?: "").trimEnd('/')

    /** `/auth/login` credentials. The access token they return lives one hour, and every login
     *  invalidates the tokens issued before it — so only the catalogue sync ever logs in. */
    val login = config["login"]?.toString() ?: ""

    val password = config["password"]?.toString() ?: ""

    /** HMAC-SHA256 key. Signs our outbound `openGame` and verifies their inbound callbacks. */
    val secretKey = config["secretKey"]?.toString() ?: ""

    /** Our API user id at the vendor — the `user_id` field of `openGame` and the `{user_id}` path
     *  segment of the catalogue. Pinning it here keeps the launch path free of any login call. */
    val userId = config["userId"]?.toString() ?: ""

    /** Per-launch override of the callback URL configured in their back office, e.g.
     *  `https://webhook.prematch.win/api/webhook/gambutsoft`. Omitted from the request when blank. */
    val callbackUrl = config["callbackUrl"]?.toString() ?: ""

    /** Where the game's exit button returns the player. `openGame` requires it and the launch
     *  command carries no lobby URL, so this is the only source. */
    val exitUrl = config["exitUrl"]?.toString() ?: ""

    /** Currencies to merge into one catalogue. The vendor serves a different game list per currency
     *  and answers HTTP 400 for a currency our account does not hold. Falls back to the currency
     *  list the login response reports. */
    val catalogCurrencies: List<String> = (config["catalogCurrencies"] as? List<*>)?.map { it.toString() }
        ?: emptyList()

    /** Language sent when the session locale is not one the games speak. */
    val language = config["language"]?.toString()?.ifBlank { null } ?: DEFAULT_LANGUAGE

    /** `player_login` for demo launches — demo sessions move no money, so the value is cosmetic. */
    val demoLogin = config["demoLogin"]?.toString()?.ifBlank { null } ?: DEFAULT_DEMO_LOGIN

    private companion object {
        const val DEFAULT_LANGUAGE = "en"

        const val DEFAULT_DEMO_LOGIN = "guest_demo_player"
    }
}
