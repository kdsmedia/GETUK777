package event.model

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,

    val playerId: String,

    val gameIdentity: String,

    val currency: String,

    val platform: String,
)
