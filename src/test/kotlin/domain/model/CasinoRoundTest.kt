package domain.model

import domain.exception.conflict.CasinoRoundAlreadyFinishedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import support.TestFixtures

class CasinoRoundTest : FunSpec({

    test("new round is not finished") {
        TestFixtures.round().isFinished shouldBe false
    }

    test("finish() returns finished round") {
        val finished = TestFixtures.round().finish()

        finished.isFinished shouldBe true
        finished.finishedAt.shouldNotBeNull()
    }

    test("double finish() throws CasinoRoundAlreadyFinishedException") {
        val finished = TestFixtures.round().finish()
        shouldThrow<CasinoRoundAlreadyFinishedException> { finished.finish() }
    }
})
