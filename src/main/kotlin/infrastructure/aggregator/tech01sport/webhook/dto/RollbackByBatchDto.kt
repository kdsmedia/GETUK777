package infrastructure.aggregator.tech01sport.webhook.dto

import kotlinx.serialization.Serializable

@Serializable
data class RollbackByBatchRequest(
    val partnerId: String,

    val items: List<WheelRollbackItem>,

    val traceId: String? = null,
)

@Serializable
data class WheelRollbackItem(
    val transactionId: Long,

    val userId: String,

    val currencyCode: String,

    val amount: String,
)
