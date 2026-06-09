package application.port.external

import domain.event.AppEvent

/**
 * Driven port for publishing a domain [AppEvent] to the event bus. The application layer depends
 * only on this port; the RabbitMQ adapter implementing it lives in infrastructure.
 */
interface IEventPublisherPort {
    fun publish(event: AppEvent<*>)
}
