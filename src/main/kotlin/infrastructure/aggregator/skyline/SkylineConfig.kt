package infrastructure.aggregator.skyline

/**
 * Skyline (skyline-software.com) integration config.
 *
 * Skyline is a game PROVIDER, not an aggregator: one RGS, one catalogue, one provider row. The
 * whole API is a single endpoint that dispatches on the `action` field of the body, and both
 * directions are signed — request and response alike travel as a bare HS256 JWT whose payload is
 * the JSON that would otherwise be the body. That signature is also the only authentication their
 * callbacks carry: they send no api key inbound.
 */
class SkylineConfig(config: Map<String, Any>) {

    /** The partner endpoint, e.g. `https://dev.skyline-api.net/api/partners/<partner>.php`.
     *  One URL serves every action; the partner is identified by the path itself. */
    val apiUrl = config["apiUrl"]?.toString()?.trim() ?: ""

    /** Sent as `apikey` on every OUTBOUND call. Their inbound callbacks do not carry it. */
    val apiKey = config["apiKey"]?.toString() ?: ""

    /** HS256 key signing both directions. Verifying it on inbound is what authenticates a callback. */
    val jwtSecret = config["jwtSecret"]?.toString() ?: ""

    /** Our casino key at the vendor, e.g. `cloud1638`. Optional on `game_launch`, required by the
     *  bonus actions. */
    val casino = config["casino"]?.toString() ?: ""

    /** The single provider every Skyline game is filed under. The feed names no provider — there
     *  is only one — so the catalogue would otherwise have nothing to group games by. */
    val providerName = config["providerName"]?.toString()?.ifBlank { null } ?: DEFAULT_PROVIDER_NAME

    /** Language sent when the session locale is blank. */
    val language = config["language"]?.toString()?.ifBlank { null } ?: DEFAULT_LANGUAGE

    /** Where the game's cashier button sends the player. Omitted from the launch when blank. */
    val cashierUrl = config["cashierUrl"]?.toString() ?: ""

    /** `player_ip` and `country` are mandatory at the vendor, and neither exists anywhere in the
     *  casino context — no session, command or proto message carries them. These stand in until
     *  they can be threaded through from the frontend; the vendor accepts placeholders on dev and
     *  wants real values in production. */
    val defaultPlayerIp = config["defaultPlayerIp"]?.toString()?.ifBlank { null } ?: DEFAULT_PLAYER_IP

    val defaultCountry = config["defaultCountry"]?.toString()?.ifBlank { null } ?: DEFAULT_COUNTRY

    /** Shown to the player on a granted free-round bonus; the vendor requires a description. */
    val bonusDescription = config["bonusDescription"]?.toString()?.ifBlank { null } ?: DEFAULT_BONUS_DESCRIPTION

    private companion object {
        const val DEFAULT_PROVIDER_NAME = "Skyline"

        const val DEFAULT_LANGUAGE = "en"

        const val DEFAULT_PLAYER_IP = "1.0.0.0"

        const val DEFAULT_COUNTRY = "UA"

        const val DEFAULT_BONUS_DESCRIPTION = "Free rounds"
    }
}
