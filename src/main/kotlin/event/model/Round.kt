package event.model

import kotlinx.serialization.Serializable

@Serializable
data class Round(
    val id: String,

    val playerId: String,

    val gameIdentity: String,

    val freespinId: String? = null,

    val finished: Boolean,
)
