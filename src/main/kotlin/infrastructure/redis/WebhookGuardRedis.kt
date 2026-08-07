package infrastructure.redis

import application.port.external.IWebhookGuardPort
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.SetArgs
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands

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

    private companion object {
        const val NONCE_PREFIX = "webhook:nonce:"

        const val ROLLBACK_PREFIX = "webhook:rolledback:"

        const val CLAIMED = "1"
    }
}
