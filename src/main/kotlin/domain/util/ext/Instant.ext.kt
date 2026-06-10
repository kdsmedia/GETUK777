package domain.util.ext

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

object InstantExt {
    fun now(): Instant = Clock.System.now()
}
