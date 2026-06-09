package domain.event

/**
 * Domain port for publishing an [AppEvent] to the event bus. The RabbitMQ adapter lives in
 * infrastructure — same split as repository contracts: the interface is part of the domain,
 * the implementation is an infrastructure adapter.
 */
interface AppEventPublisher {
    fun publish(event: AppEvent<*>)
}
