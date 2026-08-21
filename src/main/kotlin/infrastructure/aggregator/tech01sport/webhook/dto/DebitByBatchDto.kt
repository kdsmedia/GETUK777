package infrastructure.aggregator.tech01sport.webhook.dto

import kotlinx.serialization.Serializable

@Serializable
data class DebitByBatchRequest(
    val partnerId: String,

    val items: List<WheelDebitItem>,

    val traceId: String? = null,
)

@Serializable
data class WheelDebitItem(
    val userId: String,

    val transactionId: Long,

    val currencyCode: String,

    val amount: String,
)
