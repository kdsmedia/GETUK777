package infrastructure.aggregator.tech01sport.webhook.dto

import kotlinx.serialization.Serializable

@Serializable
data class RollbackBetByBatchRequest(
    val partnerId: String,

    val items: List<RollbackBetItem>,

    val traceId: String? = null,
)

@Serializable
data class RollbackBetItem(
    val transactionId: Long,

    val userId: String,

    val currencyCode: String,

    val amount: String,
)
