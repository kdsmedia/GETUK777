package infrastructure.aggregator.tech01sport.webhook.dto

import kotlinx.serialization.Serializable

@Serializable
data class PrepareCreditBetRequest(
    val privateToken: String,

    val partnerId: String,

    val currencyCode: String,

    val transactionId: Long,

    val amount: String,

    val traceId: String? = null,
)
