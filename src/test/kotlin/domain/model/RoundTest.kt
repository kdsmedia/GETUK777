package domain.model

import domain.exception.conflict.RoundAlreadyFinishedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import support.TestFixtures

class RoundTest : FunSpec({

    test("new round is not finished") {
        TestFixtures.round().isFinished shouldBe false
    }

    test("finish() returns finished round") {
        val finished = TestFixtures.round().finish()

        finished.isFinished shouldBe true
        finished.finishedAt.shouldNotBeNull()
    }

    test("double finish() throws RoundAlreadyFinishedException") {
        val finished = TestFixtures.round().finish()
        shouldThrow<RoundAlreadyFinishedException> { finished.finish() }
    }
})
