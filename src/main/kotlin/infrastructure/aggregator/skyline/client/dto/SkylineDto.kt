package infrastructure.aggregator.skyline.client.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One catalogue entry. The documented `{"result":{"games":[...]}}` wrapper does not exist — the
 * vendor confirmed `result` is the array itself — and `category` is documented but absent from the
 * feed, so it is optional here rather than assumed.
 */
@Serializable
data class SkylineGameDto(
    @SerialName("game_id") val gameId: String,

    @SerialName("game_title") val gameTitle: String = "",

    val category: String? = null,

    val images: List<SkylineImageDto> = emptyList(),
)

/** Artwork is never imported — the operator uploads covers — so this exists only to parse past it. */
@Serializable
data class SkylineImageDto(
    val url: String = "",
)

@Serializable
data class SkylineLaunchDto(
    @SerialName("launch_url") val launchUrl: String = "",
)
