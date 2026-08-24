package infrastructure.aggregator.skyline.client

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The compact HS256 JWT that carries every Skyline message, in both directions.
 *
 * There is no envelope around it: the token IS the HTTP body, and its payload is the JSON that an
 * unsigned integration would have posted. On the inbound side the signature is the ONLY thing
 * authenticating a callback — the vendor sends no api key — so a body that fails [decode] must
 * never be acted on.
 *
 * The claims are the vendor's own fields (`action`, `session`, ...); no `exp`, `iat` or `jti` is
 * involved, and their absence is why replay safety has to come from the transaction id downstream.
 */
class SkylineJwt(private val secret: String) {

    fun encode(payload: JsonObject): String {
        val signingInput = "${HEADER_B64}.${payload.toString().toByteArray(Charsets.UTF_8).b64()}"

        return "$signingInput.${hmac(signingInput).b64()}"
    }

    /** Returns null when the token is malformed or the signature does not hold — the two failures
     *  are indistinguishable to a caller on purpose, so neither reveals more than the other. */
    fun decode(token: String): JsonObject? {
        val parts = token.trim().split(TOKEN_SEPARATOR)
        if (parts.size != TOKEN_PARTS) return null

        val (header, payload, signature) = parts
        if (!verify(signingInput = "$header.$payload", signature = signature)) return null

        return runCatching { Json.parseToJsonElement(String(payload.unB64(), Charsets.UTF_8)) as? JsonObject }
            .getOrNull()
    }

    /** Constant time: a signature must not be recoverable byte by byte from response timing. */
    private fun verify(signingInput: String, signature: String): Boolean = MessageDigest.isEqual(
        hmac(signingInput),
        runCatching { signature.unB64() }.getOrElse { return false },
    )

    private fun hmac(signingInput: String): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))

        return mac.doFinal(signingInput.toByteArray(Charsets.UTF_8))
    }

    private fun ByteArray.b64(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)

    private fun String.unB64(): ByteArray = Base64.getUrlDecoder().decode(this)

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"

        const val TOKEN_SEPARATOR = '.'

        const val TOKEN_PARTS = 3

        /** `{"alg":"HS256","typ":"JWT"}` — fixed, so it is never rebuilt per message. */
        const val HEADER_B64 = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
    }
}
