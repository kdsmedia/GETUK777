package infrastructure.redis

import application.port.external.IWebhookGuardPort
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.SetArgs
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.coroutines.delay

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class WebhookGuardRedis(
    config: RedisConfig
) : IWebhookGuardPort {

    private val commands: RedisCoroutinesCommands<String, String> = RedisClient
        .create(RedisURI.builder().withHost(config.host).withPort(config.port).build())
        .connect()
        .coroutines()

    /** SET NX is what makes this atomic — two concurrent replays of one nonce cannot both win. */
    override suspend fun claimNonce(key: String, ttlSeconds: Long): Boolean =
        commands.set("$NONCE_PREFIX$key", CLAIMED, SetArgs().nx().ex(ttlSeconds)) != null

    override suspend fun markRolledBack(key: String, ttlSeconds: Long) {
        commands.set("$ROLLBACK_PREFIX$key", CLAIMED, SetArgs().ex(ttlSeconds))
    }

    override suspend fun isRolledBack(key: String): Boolean =
        commands.get("$ROLLBACK_PREFIX$key") != null

    override suspend fun <T> withLock(key: String, block: suspend () -> T): T {
        val lockKey = "$LOCK_PREFIX$key"
        val deadline = System.currentTimeMillis() + LOCK_WAIT_MILLIS

        while (commands.set(lockKey, CLAIMED, SetArgs().nx().ex(LOCK_TTL_SECONDS)) == null) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for lock $key" }
            delay(LOCK_RETRY_MILLIS)
        }

        return try {
            block()
        } finally {
            commands.del(lockKey)
        }
    }

    private companion object {
        const val NONCE_PREFIX = "webhook:nonce:"

        const val LOCK_PREFIX = "webhook:lock:"

        /** Long enough to outlive any single wallet round trip, short enough that a pod that dies
         *  holding the lock does not block the player for long. */
        const val LOCK_TTL_SECONDS = 10L

        const val LOCK_WAIT_MILLIS = 5_000L

        const val LOCK_RETRY_MILLIS = 15L

        const val ROLLBACK_PREFIX = "webhook:rolledback:"

        const val CLAIMED = "1"
    }
}
