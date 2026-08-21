package domain.model

import domain.exception.badrequest.BlankCasinoSessionTokenException
import domain.vo.ExternalCasinoRoundId
import domain.vo.FreespinId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import support.TestFixtures

class CasinoSessionTest : FunSpec({

    test("blank token is rejected") {
        shouldThrow<BlankCasinoSessionTokenException> {
            TestFixtures.session(token = "")
        }
    }

    test("openRound delegates to CasinoRoundFactory with this session") {
        val session = TestFixtures.session()
        val round = session.openRound(externalId = ExternalCasinoRoundId("rnd_42"), freespinId = null)

        round.externalId shouldBe ExternalCasinoRoundId("rnd_42")
        round.session shouldBe session
        round.freespinId shouldBe null
        round.isFinished shouldBe false
    }

    test("openRound forwards freespinId") {
        val session = TestFixtures.session()
        val round = session.openRound(externalId = ExternalCasinoRoundId("rnd_43"), freespinId = FreespinId("fs_1"))

        round.freespinId shouldBe FreespinId("fs_1")
    }
})
