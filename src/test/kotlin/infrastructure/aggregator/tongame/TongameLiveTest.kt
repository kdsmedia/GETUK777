package infrastructure.aggregator.tongame

import infrastructure.aggregator.tongame.adapter.TongameGameAdapter
import io.kotest.core.spec.style.FunSpec

class TongameLiveTest : FunSpec({

    listOf("casino-engine.prematch.casino", "casino-engine.prematch.casino:80").forEach { address ->
        test("getAggregatorGames @ $address") {
            val adapter = TongameGameAdapter(
                TongameConfig(
                    mapOf(
                        "apiUrl" to address,
                        "operatorIdentity" to "development",
                        "apiKey" to "test-deve",
                    )
                )
            )

            runCatching { adapter.getAggregatorGames() }
                .onSuccess { games ->
                    println("OK [$address] -> ${games.size} games:")
                    games.forEach { println("   - ${it.symbol} / ${it.name}") }
                }
                .onFailure { println("ERR [$address] -> ${it::class.simpleName}: ${it.message}") }
        }
    }
})
