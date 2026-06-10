package domain.vo

import domain.exception.badrequest.EmptyIdentityException
import domain.exception.badrequest.InvalidIdentityFormatException
import domain.exception.badrequest.InvalidIdentityGenerationException
import domain.exception.domainRequire
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Identity(val value: String) {
    init {
        domainRequire(value.isNotEmpty()) { EmptyIdentityException() }
        domainRequire(value.matches(SLUG)) { InvalidIdentityFormatException() }
    }

    companion object {
        /** Fleet-wide canonical slug: lowercase alphanumerics joined by single `-` or `_`. */
        private val SLUG = Regex("^[a-z0-9]+(?:[-_][a-z0-9]+)*$")

        fun generate(input: String): Identity {
            val converted = input
                .trim()
                .lowercase()
                .replace(Regex("\\s+"), "_")
                .replace(Regex("[^a-z0-9_]"), "")
                .replace(Regex("_+"), "_")
                .trimEnd('_')
                .trimStart('_')
            domainRequire(converted.isNotEmpty()) { InvalidIdentityGenerationException(input) }
            return Identity(converted)
        }
    }

    override fun toString(): String = value
}
