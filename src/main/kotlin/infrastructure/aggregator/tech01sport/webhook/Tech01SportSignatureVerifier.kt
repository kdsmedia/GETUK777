package infrastructure.aggregator.tech01sport.webhook

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.datetime.Clock

/**
 * Verifies the `betting-signature` header: `t=<unix seconds>, v1=<hmac>[, v1=<hmac>...]` where
 * `v1 = HMAC-SHA256(secret, "<t>.<raw body>")`. Several `v1` values and several active secrets
 * may coexist (key rotation) — the request is valid if any secret reproduces any received `v1`.
 * The timestamp must be within ±5 minutes of server time (doc-recommended replay protection).
 */
object Tech01SportSignatureVerifier {

    private const val HMAC_ALGORITHM = "HmacSHA256"

    private const val TIMESTAMP_TOLERANCE_SECONDS = 300L

    fun verify(header: String?, rawBody: String, secretKeys: List<String>): Boolean {
        if (header == null || secretKeys.isEmpty()) return false

        val parts = header.split(",").map { it.trim() }
        val timestamp = parts.firstOrNull { it.startsWith("t=") }?.removePrefix("t=") ?: return false
        val signatures = parts.filter { it.startsWith("v1=") }.map { it.removePrefix("v1=") }
        if (signatures.isEmpty()) return false

        val seconds = timestamp.toLongOrNull() ?: return false
        if (kotlin.math.abs(Clock.System.now().epochSeconds - seconds) > TIMESTAMP_TOLERANCE_SECONDS) return false

        val payload = "$timestamp.$rawBody"
        return secretKeys.any { key -> hmacSha256Hex(key, payload) in signatures }
    }

    private fun hmacSha256Hex(secret: String, payload: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(), HMAC_ALGORITHM))
        return mac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
