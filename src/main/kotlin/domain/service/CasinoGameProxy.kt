package domain.service

/**
 * Rewrites a provider's launch URL so the browser loads the game through our own domain instead of
 * the provider's. The provider then sees our egress address and our hostname; the player's own
 * address never reaches it.
 *
 * The upstream host is carried as ONE label in front of [BASE_DOMAIN]: dots become dashes, and a
 * literal dash is doubled first so the transform stays reversible. One label is what makes this
 * work for every provider without setup — a wildcard certificate covers exactly one, and no
 * authority issues a multi-level one.
 *
 *     https://api-ire1.214adera.shop/x  ->  https://api--ire1-214adera-shop.djmgame.com/x
 *
 * Path and query are untouched: the proxy resolves the upstream from the host alone, and a launch
 * token that survives the trip is the whole point. The proxy rewrites everything the game asks for
 * afterwards, so only this first URL is built here.
 */
object CasinoGameProxy {

    /** Our proxy's base domain — deliberately fixed: there is one proxy, and it is not per-brand. */
    const val BASE_DOMAIN = "djmgame.com"

    fun proxify(url: String): String {
        val match = ABSOLUTE_URL.matchEntire(url.trim()) ?: return url
        val host = match.groupValues[2].substringBefore(':').lowercase()
        val rest = match.groupValues[3]

        // Already ours, or not a host we can carry (a bare label has nothing to encode).
        if (host.isEmpty() || !host.contains('.')) return url
        if (host == BASE_DOMAIN || host.endsWith(".$BASE_DOMAIN")) return url

        return "https://${encode(host)}.$BASE_DOMAIN$rest"
    }

    fun encode(host: String): String = host.lowercase().replace("-", "--").replace(".", "-")

    // The port is dropped with the host: the proxy always answers on 443 and reaches upstreams on
    // their scheme default.
    private val ABSOLUTE_URL = Regex("""^(https?)://([^/?#]+)(.*)$""", RegexOption.IGNORE_CASE)
}
