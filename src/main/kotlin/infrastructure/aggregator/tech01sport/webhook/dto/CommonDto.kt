package infrastructure.aggregator.tech01sport.webhook.dto

import kotlinx.serialization.Serializable

/** Envelope for routes whose success payload is empty (ping, prepare-credit-bet, credit-bet). */
@Serializable
data class SimpleResponse(
    val code: Int,

    val description: String,
)

@Serializable
data class BatchItemResult(
    val code: Int,

    val transactionId: Long,

    val description: String? = null,

    val debt: String? = null,
)

@Serializable
data class BatchData(
    val items: List<BatchItemResult>,
)

@Serializable
data class BatchResponse(
    val code: Int,

    val description: String,

    val data: BatchData? = null,
)
