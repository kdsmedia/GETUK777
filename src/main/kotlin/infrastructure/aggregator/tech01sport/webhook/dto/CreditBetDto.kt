package infrastructure.aggregator.tech01sport.webhook.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreditBetRequest(
    val partnerId: String,

    val transactionId: Long,

    val bet: BetPayloadDto,

    val traceId: String? = null,
)
