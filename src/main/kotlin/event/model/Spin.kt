package event.model

import kotlinx.serialization.Serializable

/** Lifecycle state of a spin on the wire. Serializes to exactly Place / Settle / Rollback. */
@Serializable
enum class SpinType {
    Place,
    Settle,
    Rollback
}

@Serializable
data class Spin(
    val id: String,

    val playerId: String,

    val type: SpinType,

    val amount: Long,

    val currency: String,

    val gameIdentity: String,

    val freespinId: String? = null,
)
