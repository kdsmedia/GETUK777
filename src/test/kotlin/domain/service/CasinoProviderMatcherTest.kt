package domain.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The pairs below are the real ones from the prematch catalogue. Normalisation alone would be
 * reckless — it is only allowed to merge when the two catalogues corroborate it.
 */
class CasinoProviderMatcherTest : FunSpec({

    test("a trailing corporate word is dropped so the same vendor collapses to one key") {
        CasinoProviderMatcher.normalize("pragmatic_play") shouldBe "pragmatic"
        CasinoProviderMatcher.normalize("pragmatic") shouldBe "pragmatic"
        CasinoProviderMatcher.normalize("volt_entertainment") shouldBe "volt"
        CasinoProviderMatcher.normalize("Red Rake Gaming") shouldBe "redrake"
    }

    test("only ONE suffix is stripped, so a name is never eaten down to nothing") {
        CasinoProviderMatcher.normalize("fa_chai_gaming") shouldBe "fachai"
        CasinoProviderMatcher.normalize("play") shouldBe "play"
        CasinoProviderMatcher.normalize("games") shouldBe "games"
    }

    test("a different product of the same vendor keeps its own key") {
        // Live tables are not the slots catalogue: merging them would point every live game at an
        // aggregator that does not carry it.
        CasinoProviderMatcher.normalize("pragmatic_play_live") shouldBe "pragmaticplaylive"
    }

    test("overlap is measured against the SMALLER catalogue") {
        // 155 GamingFlow titles against 654 OneGameHub ones: judged on the 155, the smaller side,
        // or a big catalogue could never match a small one however complete the containment.
        val small = setOf("5 lions", "gates of olympus")
        val large = setOf("5 lions", "gates of olympus", "big bass bonanza", "sweet bonanza")

        CasinoProviderMatcher.catalogOverlap(small, large) shouldBe 1.0
        CasinoProviderMatcher.catalogOverlap(large, small) shouldBe 1.0
    }

    test("an empty catalogue never corroborates anything") {
        CasinoProviderMatcher.catalogOverlap(emptySet(), setOf("5 lions")) shouldBe 0.0
        CasinoProviderMatcher.catalogOverlap(setOf("5 lions"), emptySet()) shouldBe 0.0
    }

    test("the real pragmatic pair merges: same key AND the catalogues agree") {
        CasinoProviderMatcher.isSameVendor(
            leftName = "pragmatic_play",
            rightName = "pragmatic",
            leftCatalog = setOf("5 lions", "gates of olympus", "sweet bonanza"),
            rightCatalog = setOf("5 lions", "gates of olympus"),
        ) shouldBe true
    }

    test("a matching name with unrelated catalogues does NOT merge") {
        CasinoProviderMatcher.isSameVendor(
            leftName = "pragmatic_play",
            rightName = "pragmatic",
            leftCatalog = setOf("gates of olympus", "sweet bonanza"),
            rightCatalog = setOf("something else entirely"),
        ) shouldBe false
    }

    test("vendors that merely look alike are left alone") {
        // egt/amusnet IS the same vendor, but nothing in the names says so — that pair needs a
        // configured alias, and the matcher must not guess at it.
        CasinoProviderMatcher.isSameVendor(
            leftName = "amusnet",
            rightName = "egt",
            leftCatalog = setOf("100 burning hot"),
            rightCatalog = setOf("100 burning hot"),
        ) shouldBe false
    }
})
