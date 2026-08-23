package infrastructure.persistence.search

/** The condition a listing ended up searching with, and how many rows it matches. */
data class SearchPass<C>(
    val condition: C,

    val totalItems: Long,
)

/**
 * Runs the strict, index-backed search first and falls back to the wide net only when the strict
 * pass matched nothing at all.
 *
 * A player who typed something the catalog recognises never pays for the fallback and never sees
 * its noise; a player who mistyped badly gets the closest games instead of an empty screen. The
 * extra round trip happens only on a search that already failed.
 *
 * [relaxable] is the caller's own gate — there is no point re-running a query that has nothing left
 * to relax (nothing typed, or only tokens too short to guess at); see [searchCanRelax].
 */
fun <C> searchPass(
    relaxable: Boolean,
    condition: (relaxed: Boolean) -> C,
    count: (C) -> Long,
): SearchPass<C> {
    val strict = condition(false)
    val strictTotal = count(strict)

    if (!relaxable || strictTotal > 0) return SearchPass(strict, strictTotal)

    val relaxed = condition(true)

    return SearchPass(relaxed, count(relaxed))
}
