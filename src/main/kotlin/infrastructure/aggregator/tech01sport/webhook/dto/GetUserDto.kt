package infrastructure.aggregator.tech01sport.webhook.dto

import kotlinx.serialization.Serializable

@Serializable
data class GetUserRequest(
    val userId: String,

    val partnerId: String,

    val traceId: String? = null,
)

@Serializable
data class GetUserWalletDto(
    val amount: String,

    val currencyCode: String,

    val typeId: Int,
)

@Serializable
data class GetUserData(
    val userData: UserDataDto,

    val wallets: List<GetUserWalletDto> = emptyList(),

    val activeCurrencyCode: String? = null,
)

@Serializable
data class GetUserResponse(
    val code: Int,

    val description: String,

    val data: GetUserData? = null,
)
