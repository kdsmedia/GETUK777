package infrastructure.persistence.search

import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.append
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.stringParam

/** Postgres helpers created by `V11__fuzzy_search.sql`. */
private const val NORMALIZE_FUNCTION = "casino_search_norm"

private const val PHONETIC_FUNCTION = "casino_search_phonetic"

/** A longer query is a paste, not a search — the tail only slows the scan down. */
private const val MAX_QUERY_LENGTH = 96

private const val MAX_TOKENS = 6

/** Shorter words carry too few consonants for a metaphone code to mean anything. */
private const val MIN_PHONETIC_LENGTH = 4

private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

/**
 * Mirror of the SQL `casino_search_norm`: lower case, punctuation collapsed to single spaces.
 * The two must agree, otherwise a query token would not be searched the way the column was indexed.
 */
fun normalizeSearchQuery(raw: String): String =
    SEPARATORS.replace(raw.lowercase(), " ").trim().take(MAX_QUERY_LENGTH).trim()

/**
 * Fuzzy, order-free, typo-tolerant matching over a set of columns — the searchable "haystack" of
 * one aggregate (a game's name + identity, a provider's name + identity + aliases, …).
 *
 * [matches] ORs three branches, every one of them served by the GIN indexes of `V11__fuzzy_search.sql`:
 *
 *  1. **every token** of the query is either a substring of the haystack (a fragment from either
 *     side: "olymp", "gate") or trigram-similar to some word of it (`<%`, so "bonanca" still finds
 *     *Sweet Bonanza*). Tokens are ANDed, so word order and missing words don't matter — "gates
 *     olimpus" finds *Gates of Olympus*;
 *  2. the **whole phrase** is trigram-similar to the haystack, which covers a query typed without
 *     spaces ("bookofra" → *Book of Ra Deluxe*);
 *  3. the **double-metaphone** codes of the query are all present among the haystack's codes. This
 *     is what survives the misspellings trigrams give up on — "rulet" → *Roulette*, "gaets" →
 *     *Gates*, "krown" → *Crown*.
 *
 * The trigram threshold for (1) and (2) is the session-wide `pg_trgm.word_similarity_threshold`
 * set in [infrastructure.persistence.DatabaseFactory].
 *
 * The column list handed to the constructor MUST match the expression indexed in
 * `V11__fuzzy_search.sql` — Postgres matches an expression index structurally.
 */
class SearchIndex(vararg parts: Expression<*>) {

    private val text: Expression<String> = SqlTextFunction(NORMALIZE_FUNCTION, parts.toList())

    private val phonetic: Expression<String> = SqlTextFunction(PHONETIC_FUNCTION, parts.toList())

    fun matches(rawQuery: String): Op<Boolean> {
        val needle = normalizeSearchQuery(rawQuery)
        if (needle.isEmpty()) return Op.TRUE

        val tokens = needle.split(' ').take(MAX_TOKENS)

        val branches = buildList<Op<Boolean>> {
            add(tokens.map { token -> containsFragment(token) or wordSimilar(token) }.reduce { acc, op -> acc and op })

            if (tokens.size > 1) {
                add(wordSimilar(needle))
            }

            if (tokens.any { it.length >= MIN_PHONETIC_LENGTH }) {
                add(soundsLike(needle))
            }
        }

        return branches.reduce { acc, op -> acc or op }
    }

    /**
     * Ordering key for a searched listing: an exact prefix beats an exact fragment beats a fuzzy
     * hit, and inside each band the trigram score decides. `null` when nothing was typed, so the
     * caller keeps its own catalog ordering untouched.
     */
    fun relevance(rawQuery: String): Expression<Double>? {
        val needle = normalizeSearchQuery(rawQuery)
        if (needle.isEmpty()) return null

        return Relevance(text, needle)
    }

    fun relevanceOrdering(rawQuery: String): Array<Pair<Expression<*>, SortOrder>> =
        relevance(rawQuery)
            ?.let { arrayOf<Pair<Expression<*>, SortOrder>>(it to SortOrder.DESC) }
            ?: emptyArray()

    private fun containsFragment(token: String): Op<Boolean> = Op.build { text like "%$token%" }

    private fun wordSimilar(needle: String): Op<Boolean> = WordSimilarityOp(stringParam(needle), text)

    private fun soundsLike(needle: String): Op<Boolean> = PhoneticContainsOp(phonetic, stringParam(needle))
}

/** `fn(part1 || ' ' || part2 || …)` — the exact shape the expression indexes are built on. */
private class SqlTextFunction(
    private val functionName: String,
    private val parts: List<Expression<*>>,
) : Expression<String>() {

    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        +functionName
        +"("
        parts.forEachIndexed { index, part ->
            if (index > 0) +" || ' ' || "
            +part
        }
        +")"
    }
}

/** pg_trgm word similarity: the needle resembles some word-extent of the haystack. */
private class WordSimilarityOp(
    private val needle: Expression<String>,
    private val haystack: Expression<String>,
) : Op<Boolean>() {

    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        append('(', needle, " <% ", haystack, ')')
    }
}

/**
 * Every metaphone code of the needle is present in the haystack's codes. The needle's codes are
 * computed by a scalar sub-select so the planner evaluates them once per scan (as an InitPlan)
 * instead of once per row.
 */
private class PhoneticContainsOp(
    private val haystack: Expression<String>,
    private val needle: Expression<String>,
) : Op<Boolean>() {

    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        append('(', haystack, " @> (SELECT ", PHONETIC_FUNCTION, "(", needle, ")))")
    }
}

private class Relevance(
    private val text: Expression<String>,
    private val needle: String,
) : Expression<Double>() {

    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("(CASE WHEN ", text, " LIKE ", stringParam("$needle%"), " THEN 3.0")
        append(" WHEN ", text, " LIKE ", stringParam("%$needle%"), " THEN 2.0 ELSE 0.0 END")
        append(" + word_similarity(", stringParam(needle), ", ", text, "))")
    }
}
