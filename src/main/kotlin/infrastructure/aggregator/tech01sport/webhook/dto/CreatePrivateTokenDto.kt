package infrastructure.aggregator.tech01sport.webhook.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreatePrivateTokenRequest(
    val publicToken: String,

    val partnerId: String,

    val traceId: String? = null,
)

@Serializable
data class UserDataDto(
    val id: String,

    val country: String = "",

    val meta: List<UserMetaDto> = emptyList(),
)

@Serializable
data class UserMetaDto(
    val key: String,

    val value: String,
)

@Serializable
data class SessionDataDto(
    val deviceType: String = "web-desktop",
)

@Serializable
data class CreatePrivateTokenData(
    val privateToken: String,

    val sessionData: SessionDataDto = SessionDataDto(),

    val userData: UserDataDto,
)

@Serializable
data class CreatePrivateTokenResponse(
    val code: Int,

    val description: String,

    val data: CreatePrivateTokenData? = null,
)
