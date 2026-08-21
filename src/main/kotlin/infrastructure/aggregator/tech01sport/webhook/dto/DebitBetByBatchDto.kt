package infrastructure.aggregator.tech01sport.webhook.dto

import kotlinx.serialization.Serializable

@Serializable
data class DebitBetByBatchRequest(
    val partnerId: String,

    val items: List<DebitBetItem>,

    val traceId: String? = null,
)

@Serializable
data class DebitBetItem(
    val userId: String,

    val transactionId: Long,

    val currencyCode: String,

    val amount: String,

    val bonusAmount: String? = null,

    val bet: BetPayloadDto,
)
