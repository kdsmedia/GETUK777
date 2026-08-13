package domain.model

import domain.exception.conflict.FreespinExhaustedException
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.FreespinId
import domain.vo.PlayerId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import support.TestFixtures

class FreespinTest : FunSpec({

    val start = Instant.parse("2026-08-13T00:00:00Z")
    val end = Instant.parse("2026-08-20T00:00:00Z")

    fun freespin(remaining: Int = 10, cancelledAt: Instant? = null) = Freespin(
        referenceId = FreespinId("bonus-1"),
        playerId = PlayerId("1"),
        gameVariant = TestFixtures.gameVariant(),
        currency = Currency("UAH"),
        spinAmount = Amount(25),
        totalCount = 10,
        remainingCount = remaining,
        startAt = start,
        endAt = end,
        cancelledAt = cancelledAt,
    )

    test("charging spends rounds and leaves the rest") {
        freespin().charge(3).remainingCount shouldBe 7
    }

    test("charging more than is left is refused rather than going negative") {
        shouldThrow<FreespinExhaustedException> { freespin(remaining = 2).charge(3) }
    }

    test("a non-positive charge is refused") {
        shouldThrow<FreespinExhaustedException> { freespin().charge(0) }
    }

    test("cancelling empties the grant, whatever the provider keeps on its side") {
        val cancelled = freespin().cancel()

        cancelled.remainingCount shouldBe 0
        cancelled.isCancelled shouldBe true
        cancelled.isRedeemableAt(start) shouldBe false
    }

    test("redeemable only inside the window, and only while rounds remain") {
        freespin().isRedeemableAt(start) shouldBe true
        freespin().isRedeemableAt(Instant.parse("2026-08-15T12:00:00Z")) shouldBe true

        freespin().isRedeemableAt(Instant.parse("2026-08-12T23:59:59Z")) shouldBe false
        // Half-open: the end instant itself is already expired.
        freespin().isRedeemableAt(end) shouldBe false
        freespin(remaining = 0).isRedeemableAt(start) shouldBe false
    }
})
