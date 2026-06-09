package domain.model

import kotlinx.serialization.Serializable

/**
 * Platform type for game variants.
 */
@Serializable
enum class Platform {
    DESKTOP,
    MOBILE,
    DOWNLOAD
}
