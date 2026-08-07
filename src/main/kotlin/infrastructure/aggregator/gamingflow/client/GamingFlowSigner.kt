package infrastructure.aggregator.gamingflow.client

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * HMAC request signing for the GamingFlow signature endpoint.
 *
 * The signed payload is `big_endian_uint64(nonce) + big_endian_uint32(timestamp) + body`, hashed
 * with HMAC-SHA256 under the secret key. The header value carries the key id so the receiver knows
 * which key to verify with: `X-Signature: <keyId>=<hex>`.
 *
 * The nonce must be unique within its TTL (the provider recommends one minute) — replaying one is
 * what the receiving side rejects to stop replay attacks. [nextNonce] draws from a process-wide
 * monotonic counter seeded off wall-clock micros, so it never repeats within a process and cannot
 * realistically collide across pods inside the TTL window.
 */
class GamingFlowSigner(
    private val keyId: String,
    private val keyValue: String,
) {

    fun nextNonce(): String = java.lang.Long.toUnsignedString(NONCE_COUNTER.incrementAndGet())

    /** Signs [body] for the given [nonce] and [timestamp] (UNIX seconds). Reused verbatim when
     *  answering an inbound request: the response is signed with the nonce/timestamp we received. */
    fun sign(body: String, nonce: String, timestamp: Long): String {
        val prefix = ByteBuffer.allocate(NONCE_BYTES + TIMESTAMP_BYTES)
            .putLong(java.lang.Long.parseUnsignedLong(nonce))
            .putInt(timestamp.toInt())
            .array()

        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(keyValue.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))
        mac.update(prefix)
        mac.update(body.toByteArray(Charsets.UTF_8))

        return "$keyId$KEY_SEPARATOR${mac.doFinal().joinToString("") { "%02x".format(it) }}"
    }

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"

        const val KEY_SEPARATOR = "="

        const val NONCE_BYTES = 8

        const val TIMESTAMP_BYTES = 4

        const val NONCE_SEED_SCALE = 1_000L

        val NONCE_COUNTER = AtomicLong(
            System.currentTimeMillis() * NONCE_SEED_SCALE + Random.nextLong(NONCE_SEED_SCALE)
        )
    }
}
