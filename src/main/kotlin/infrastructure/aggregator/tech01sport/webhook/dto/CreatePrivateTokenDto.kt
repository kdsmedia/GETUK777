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
)

@Serializable
data class CreatePrivateTokenData(
    val privateToken: String,

    val userData: UserDataDto,
)

@Serializable
data class CreatePrivateTokenResponse(
    val code: Int,

    val description: String,

    val data: CreatePrivateTokenData? = null,
)
