package infrastructure.aggregator.onegamehub

import infrastructure.aggregator.onegamehub.client.dto.CreateFreespinDto
import infrastructure.aggregator.onegamehub.client.dto.ResponseDto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The provider validates the wire shape strictly and answers 400 with the reason. Both are pinned
 * here because neither is visible from our own types: an ISO `T` in the dates made every
 * freerounds_create fail, and the reason was invisible until `message` was modelled.
 */
class OneGameHubFreespinWireTest : FunSpec({

    val json = Json { encodeDefaults = true }

    test("dates go out as %Y-%m-%d %H:%M:%S, not ISO") {
        val payload = CreateFreespinDto(
            id = "bonus-1",
            startAt = LocalDateTime(2026, 8, 13, 12, 20, 0),
            endAt = LocalDateTime(2026, 8, 20, 9, 5, 7),
            number = 10,
            playerId = "1",
            currency = "UAH",
            gameId = "pragmatic-play-gates-of-olympus",
            bet = 100,
            lineNumber = 20,
        )

        val encoded = json.encodeToString(CreateFreespinDto.serializer(), payload)

        encoded shouldContain "\"start_at\":\"2026-08-13 12:20:00\""
        // Zero-padded throughout — the provider's template has no room for a single-digit hour.
        encoded shouldContain "\"end_at\":\"2026-08-20 09:05:07\""
    }

    test("a rejection carries the provider's own explanation") {
        val decoded = json.decodeFromString(
            ResponseDto.serializer(String.serializer()),
            """{"status": 400, "message": "`line_number` should be a positive integer"}""",
        )

        decoded.success shouldBe false
        decoded.describe() shouldBe "400: `line_number` should be a positive integer"
    }

    test("a success describes itself with the status alone") {
        val decoded = json.decodeFromString(
            ResponseDto.serializer(String.serializer()),
            """{"status": 200}""",
        )

        decoded.success shouldBe true
        decoded.describe() shouldBe "200"
    }
})
