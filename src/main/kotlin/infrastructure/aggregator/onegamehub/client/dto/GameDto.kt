package infrastructure.aggregator.onegamehub.client.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GameDto(
    val id: String,

    val name: String,

    val brand: String,

    val provider: String = "",

    val categories: List<String> = emptyList(),

    val subcategories: List<String> = emptyList(),

    val media: MediaDto? = null,

    @SerialName("is_free_rounds_supported")
    val freespinEnable: Boolean,

    @SerialName("is_demo_supported")
    val demoEnable: Boolean,

    val paylines: Int = 0
)

@Serializable
data class MediaDto(
    val icon: String? = null,

    val thumbnails: Map<String, String> = emptyMap(),
)
