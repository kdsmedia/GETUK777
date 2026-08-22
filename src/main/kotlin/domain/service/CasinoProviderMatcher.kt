package domain.service

/**
 * Decides whether two aggregators are talking about the same vendor under different names.
 *
 * Providers are keyed by an identity derived from whatever the aggregator calls them, so one vendor
 * listed as `egt` by one and `amusnet` by another looks like two vendors and its whole catalog gets
 * imported twice. A hand-written alias map fixes the pairs somebody already noticed; the ones
 * nobody noticed surface as a duplicate provider whose games are all inactive — which is exactly
 * how GamingFlow's Pragmatic sat unused next to OneGameHub's `pragmatic_play`.
 *
 * Two signals, and both must agree before anything is merged:
 *
 *  - [normalize] strips the corporate noise vendors decorate their names with, so `pragmatic_play`
 *    and `pragmatic` collapse to the same key;
 *  - [catalogOverlap] then checks the two catalogs actually describe the same games, because a
 *    matching name alone is not evidence — `pragmatic_play_live` normalizes close to
 *    `pragmatic_play` and is a different product.
 */
object CasinoProviderMatcher {

    /**
     * Share of the smaller catalog that both sides list. Below [MIN_CATALOG_OVERLAP] the two are
     * treated as different vendors and imported separately, leaving the call to a human.
     */
    const val MIN_CATALOG_OVERLAP = 0.30

    private val VENDOR_SUFFIXES = listOf(
        "entertainment", "interactive", "studios", "studio", "gaming", "games", "group", "play",
    )

    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]")

    /**
     * Canonical key for a provider name: case and punctuation dropped, then one trailing corporate
     * word removed. Only one — `fa_chai_gaming` becomes `fachai`, but stripping repeatedly would
     * eat real names down to nothing.
     */
    fun normalize(name: String): String {
        val flat = NON_ALPHANUMERIC.replace(name.lowercase(), "")
        val suffix = VENDOR_SUFFIXES.firstOrNull { flat.length > it.length && flat.endsWith(it) }
        return if (suffix == null) flat else flat.dropLast(suffix.length)
    }

    /** Fraction of the SMALLER catalog present in both. Zero when either side is empty. */
    fun catalogOverlap(left: Set<String>, right: Set<String>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val shared = left.count { it in right }
        return shared.toDouble() / minOf(left.size, right.size)
    }

    /** True when the names collapse to the same key AND the catalogs corroborate it. */
    fun isSameVendor(
        leftName: String,
        rightName: String,
        leftCatalog: Set<String>,
        rightCatalog: Set<String>,
    ): Boolean = normalize(leftName) == normalize(rightName) &&
        catalogOverlap(leftCatalog, rightCatalog) >= MIN_CATALOG_OVERLAP
}
