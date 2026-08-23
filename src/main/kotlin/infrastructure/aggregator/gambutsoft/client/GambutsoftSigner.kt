package infrastructure.aggregator.gambutsoft.client

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * `X-Signature` for both directions: lowercase hex HMAC-SHA256 over the RAW request body bytes.
 *
 * The hash covers the exact bytes, not the parsed object — a body re-encoded between signing and
 * sending (or parsed and re-serialized before verifying) produces a different signature, which is
 * why every caller here works with the request string.
 *
 * The scheme carries no nonce and no timestamp, so the signature alone does not make a captured
 * request unusable a second time. Replay safety comes from the transaction id downstream.
 */
class GambutsoftSigner(private val secretKey: String) {

    fun sign(body: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))

        return mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** Compared in constant time so the expected value cannot be recovered byte by byte from
     *  response timing. Case is normalized first — the hex is the secret, its casing is not. */
    fun verify(body: String, signature: String?): Boolean {
        if (signature == null || secretKey.isBlank()) return false

        return MessageDigest.isEqual(
            sign(body).toByteArray(Charsets.UTF_8),
            signature.trim().lowercase().toByteArray(Charsets.UTF_8),
        )
    }

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
    }
}
