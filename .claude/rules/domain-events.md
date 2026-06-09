# Events (AppEvent envelope)

Events use the fleet-wide crm envelope pattern. Every event is published as a uniform
`{ "playerId": <key>, "data": {<domain model>} }` envelope on a `<domain>.events` route.
The `data` is the **domain aggregate itself** — there are no snapshot twins and no mappers.
The JSON codec + channel live ONLY in the RabbitMQ adapter (`infrastructure/rabbitmq/AppEventBus.kt`);
no use case or event touches JSON, bytes, or the channel.

## Where things live

- **Events are a domain concern** — they live in `domain/event/`:
  - `AppEvent<T>` interface + its `Meta<T>` companion contract (`route` + `serializer` + `create`).
  - `SpinEvent` / `RoundEvent` / `SessionEvent` — each wraps a `domain.model.*` aggregate directly.
- **The publisher is a driven port** — `application/port/external/IEventPublisherPort` (`publish(AppEvent<*>)`),
  following the project `I…Port` convention. Use cases depend on this port only.
- **The RabbitMQ machinery is infrastructure** — `infrastructure/rabbitmq/AppEventBus.kt`:
  `RabbitAppEventPublisher` (impl of the port, owns `appJson` + channel + envelope),
  `NoOpAppEventPublisher` (sync CLI), the generic `AppEventConsumer<E>` base, `EVENT_EXCHANGE`,
  `declareEventExchange`. RabbitMQ/coroutines never appear in `domain/`.

## Rules

- **Publish the domain model AS-IS.** `data` is the domain aggregate (`Spin`/`Round`/`Session`),
  not a snapshot/DTO. The aggregates are `@Serializable`, so the full nested graph ships verbatim
  — including `Spin.reference` (recursive) and `aggregator.config` secrets via
  `domain/util/AnyMapSerializer`. This is a deliberate owner decision; do NOT reintroduce
  snapshot twins or `toModel()` mappers.
- **Per-model, not per-action.** One event per domain model. Lifecycle state lives in the model
  (`Spin.type`), never as separate `*Placed`/`*Settled` types or per-action routing keys. No `change_type`.
- **One constructor param.** Each event takes one `data` param; `playerId` is *derived* from `data`
  (e.g. `data.round.session.playerId.value`), never passed separately.
- **Computed members don't ship.** kotlinx serializes only constructor state, so getter-only
  properties (`Spin.isPlace`, `Round.isFinished`) never appear on the wire.
- **Publish after commit.** Use cases publish **after** the DB write commits (outside the
  `dbTransaction { }` block) so a failed transaction never emits phantom events.
- **Consumers are read-only routers.** An `AppEventConsumer<E>` subclass `handle()` only maps the
  event and invokes a use case — no business logic, never publishes. The delivery callback is
  wrapped in try/catch so a poison/failed delivery can never close the shared channel
  (the 2026-06-09 outage — do NOT remove it).
- **Auto-ack, at-most-once.** The base consumes with auto-ack and runs `handle()` via `runBlocking`.
  A failed handler is logged (not requeued); these consumers feed at-most-once Redis projections.

## Adding a new event

1. Make the domain model `@Serializable` (and any VO/enum it reaches that isn't already).
2. Add `domain/event/<Name>Event.kt`: one `data: <domain model>` param, `playerId` derived from
   `data`, and a companion `Meta` with `route = "<domain>.events"`, `serializer`, `create(data)`.
3. Publish it from the relevant use case after the write commits via `IEventPublisherPort`.

## Existing events

- `SessionEvent` → `session.events` — published by `OpenSessionUsecase` after the session persists.
- `SpinEvent` → `spin.events` — published by `ProcessSpinUsecase` after the spin persists
  (`Spin.type` serializes as `PLACE`/`SETTLE`/`ROLLBACK`).
- `RoundEvent` → `round.events` — published by `FinishRoundUsecase` after the round persists
  (`finished = true`). Round-finished is a `RoundEvent`, NOT a spin type.

## Cross-engine contract

`spin.events` / `round.events` / `session.events` are consumed by **crm-engine** (bonus-engine is
decommissioned). The wire shape is now the full domain aggregate graph — changing a domain model's
fields/VOs changes the wire, so coordinate with crm ingestion.
