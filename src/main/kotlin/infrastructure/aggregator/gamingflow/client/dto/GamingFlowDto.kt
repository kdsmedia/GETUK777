package infrastructure.aggregator.gamingflow.client.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GameListResultDto(
    @SerialName("Games") val games: List<GameDto> = emptyList(),
)

/**
 * One entry of `Game.List`. `Settings` is omitted — it only materializes when the request carries
 * a `BankGroupId`, and nothing in the catalog sync needs bet levels or denominations.
 */
@Serializable
data class GameDto(
    @SerialName("Id") val id: String,

    @SerialName("Name") val name: String = "",

    @SerialName("Description") val description: String = "",

    @SerialName("SectionId") val sectionId: String = "",

    @SerialName("Type") val type: String = "",

    @SerialName("Tags") val tags: List<String> = emptyList(),

    @SerialName("Format") val format: String = "",

    /** Line count. A ways-based game reports a range instead of a number, e.g. `243-3125w`. */
    @SerialName("LinesCount") val linesCount: String = "",

    /** Multiplied by a denomination to derive the in-game bet. */
    @SerialName("BaseBet") val baseBet: Int = 0,
)

@Serializable
data class SessionResultDto(
    @SerialName("SessionId") val sessionId: String,

    /** Deprecated provider-side URL — the domain may change under us, so we compose the launch URL
     *  from `BaseHost` and only fall back to this when no base host is configured. */
    @SerialName("SessionUrl") val sessionUrl: String = "",
)
