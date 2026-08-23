package infrastructure.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

/**
 * How close a misspelt word has to be to a catalog word before the search accepts it
 * (pg_trgm's `<%`). The default 0.6 only forgives a single wrong letter in a long word;
 * 0.45 covers what players actually type ("bonanca", "starbrust") without dragging in noise.
 * It is a session setting, so it goes on every pooled connection.
 */
private const val WORD_SIMILARITY_THRESHOLD = "SET pg_trgm.word_similarity_threshold = 0.45"

object DatabaseFactory {

    fun init(config: DatabaseConfig) {
        val dataSource = createHikariDataSource(config)
        Database.connect(dataSource)
    }

    private fun createHikariDataSource(config: DatabaseConfig): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = config.maxPoolSize
            minimumIdle = config.minIdle
            driverClassName = "org.postgresql.Driver"
            isAutoCommit = false
            connectionInitSql = WORD_SIMILARITY_THRESHOLD
        }
        return HikariDataSource(hikariConfig)
    }
}
