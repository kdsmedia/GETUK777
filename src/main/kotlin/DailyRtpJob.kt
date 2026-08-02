import application.Bus
import application.command.game.RecalculateGameRtpCommand
import application.port.external.IEventPublisherPort
import domain.util.ext.InstantExt
import infrastructure.koin.aggregatorModule
import infrastructure.koin.busModule
import infrastructure.koin.configModule
import infrastructure.koin.externalModule
import infrastructure.koin.handlerModule
import infrastructure.koin.persistenceModule
import infrastructure.koin.usecaseModule
import infrastructure.persistence.DatabaseConfig
import infrastructure.persistence.DatabaseFactory
import infrastructure.rabbitmq.NoOpAppEventPublisher
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.nekgamebling.DailyRtpJob")

// The job runs every 24h, so the recalculation window is the last 24 hours —
// back-to-back runs cover the spin stream with no gaps and no overlap-loss.
private val RTP_WINDOW = 24.hours

private val dailyRtpOverrideModule = module {
    // The RTP job does not publish events and must not open a RabbitMQ channel.
    single<IEventPublisherPort> { NoOpAppEventPublisher }
}

fun main() {
    System.setProperty("user.timezone", "UTC")
    java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))

    val koinApp = startKoin {
        slf4jLogger()
        allowOverride(true)
        modules(
            configModule,
            persistenceModule,
            externalModule,
            dailyRtpOverrideModule,
            usecaseModule,
            handlerModule,
            busModule,
            aggregatorModule
        )
    }

    val koin = koinApp.koin

    val dbConfig = koin.get<DatabaseConfig>()
    DatabaseFactory.init(dbConfig)

    runBlocking {
        val bus = koin.get<Bus>()

        val since = InstantExt.now() - RTP_WINDOW
        logger.info("Recalculating game RTP from spins since {}...", since)
        val updated = bus(RecalculateGameRtpCommand(since))
        logger.info("Game RTP recalculated: {} game(s) updated", updated)
    }
}
