package infrastructure.persistence.search

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The Kotlin normalizer must agree with the SQL `casino_search_norm` of V11__fuzzy_search.sql —
 * a query token normalized differently from the indexed column simply never matches.
 */
class NormalizeSearchQueryTest : FunSpec({

    test("lower cases the query") {
        normalizeSearchQuery("Book Of Ra") shouldBe "book of ra"
    }

    test("collapses punctuation and repeated spaces into single separators") {
        normalizeSearchQuery("gates-of__olympus!!  1000") shouldBe "gates of olympus 1000"
    }

    test("trims the edges") {
        normalizeSearchQuery("  ...starburst.. ") shouldBe "starburst"
    }

    test("keeps non-latin letters") {
        normalizeSearchQuery("Книга Ра") shouldBe "книга ра"
    }

    test("a query of only separators normalizes to nothing") {
        normalizeSearchQuery(" -- ") shouldBe ""
    }

    test("caps an over-long query without leaving a dangling separator") {
        val long = "a".repeat(95) + " bonanza"
        normalizeSearchQuery(long) shouldBe "a".repeat(95)
    }
})
