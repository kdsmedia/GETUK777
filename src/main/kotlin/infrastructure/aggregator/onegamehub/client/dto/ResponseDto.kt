package infrastructure.aggregator.onegamehub.client.dto

import kotlinx.serialization.Serializable

@Serializable
data class ResponseDto<T>(
    val status: Int,

    val response: T? = null,

    /** The provider explains every rejection here; without it a caller only sees the status. */
    val message: String? = null
) {
    val success: Boolean = status == 200

    /** Status plus whatever the provider said about it, for error messages worth reading. */
    fun describe(): String = message?.let { "$status: $it" } ?: status.toString()
}
