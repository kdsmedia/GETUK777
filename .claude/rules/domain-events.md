# Events (AppEvent envelope)

Events use the fleet-wide crm envelope pattern. Every event is published as a uniform
`{ "playerId": <key>, "data": {<model>} }` envelope on a `<domain>.events` route. The codec
lives ONLY in the central `event/AppEvent.kt` — no use case, event, or consumer touches JSON,
bytes, or the channel.

## Rules

- **Per-model, not per-action.** One event per domain model carrying the full snapshot.
  Lifecycle state lives in the model (`Spin.type`), never as separate `*Placed`/`*Settled`
  event types or per-action routing keys. No `change_type` field.
- **One constructor param.** Each event has exactly one `data` param; `playerId` is *derived*
  from `data`, never passed separately.
- **`data` is always a `@Serializable` wire snapshot** (`event/model/`), never a domain
  aggregate, ORM entity, or transaction-bound object. Map domain → snapshot with a
  `toModel()` extension (`event/mapper/`) at publish time.
- **Publish after commit.** Use cases publish **after** the DB write commits (outside the
  `dbTransaction { }` block) so a failed transaction never emits phantom events.
- **Consumers are read-only routers.** An `AppEventConsumer<E>` subclass `handle()` only maps
  the event and invokes a use case — no business logic, no `if/else` on domain state beyond
  routing, and it NEVER publishes.
- **Auto-ack, at-most-once.** Mirroring the crm reference, the base consumes with auto-ack and
  runs `handle()` via `runBlocking` in the delivery callback. A failed handler is logged (not
  requeued); these consumers feed at-most-once Redis projections.

## Adding a new event

1. Add a `@Serializable` snapshot to `event/model/<Name>.kt` (if a new model).
2. Add the `domain.X.toModel()` mapper in `event/mapper/<Name>Mapper.kt`.
3. Add `event/<Name>Event.kt`: one `data` param, `playerId = data.playerId`, and a companion
   `Meta` with `route = "<domain>.events"`, `serializer`, and `create(data)`.
4. Publish it from the relevant use case after the write commits via `AppEventPublisher`.

## Existing events

- `SessionEvent` → `session.events` — published by `OpenSessionUsecase` after the session persists.
- `SpinEvent` → `spin.events` — published by `ProcessSpinUsecase` after the spin persists
  (`Spin.type` carries Place/Settle/Rollback).
- `RoundEvent` → `round.events` — published by `FinishRoundUsecase` after the round persists
  (`finished = true`). Round-finished is a `RoundEvent`, NOT a spin type.

## Cross-engine contract

`spin.events` / `round.events` / `session.events` are consumed by bonus-engine — the JSON field
names and primitive shapes (and the `SpinType` strings `Place`/`Settle`/`Rollback`) are a hard
contract. Changing a wire snapshot in `event/model/` breaks bonus-engine.
