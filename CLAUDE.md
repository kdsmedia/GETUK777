# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

**casino-engine** — Kotlin/Ktor microservice serving as the casino game engine for the IGambling platform. Manages game catalog, sessions, betting rounds/spins, and aggregator integrations.

Part of the IGambling platform — see the parent `CLAUDE.md` at `/IGambling/CLAUDE.md` for full platform context.

## Build Commands

```bash
./gradlew build                                    # Build (also runs installDist)
./gradlew test                                     # Run full Kotest suite (JUnit 5 platform)
./gradlew test --tests "domain.service.SpinBalanceCalculatorTest"  # Single spec
./gradlew test --rerun-tasks                       # Force re-run (test task caches results)
./gradlew run                                      # Run application (HTTP :8080, gRPC :5050)
./gradlew generateProto                            # Generate gRPC stubs from proto files
./gradlew runSync                                  # Run aggregator sync CLI locally
```

Test framework: **Kotest 5.9.1** (`FunSpec`) on the JUnit 5 platform, with mockk and kotlinx-coroutines-test. Testcontainers deps are wired but repository/integration tests are not yet written.

## Local Development

```bash
# 1. Start infrastructure
docker-compose up -d postgres rabbitmq redis minio minio-init

# 2. Configure environment
cp .env.example .env           # Defaults point to localhost

# 3. Run the application
./gradlew run                  # Starts HTTP on :8080, gRPC on :5050
```

Full stack (infra + app in Docker):
```bash
./gradlew build                # Creates build/distributions/casino-engine-*.tar
docker-compose up -d
```

Two application entrypoints in Docker:
- `/app/bin/casino-engine` — main server (HTTP + gRPC + consumers)
- `/app/bin/sync-aggregators` — one-shot aggregator game sync job

## Architecture

Hexagonal Architecture + DDD + CQRS. Kotlin 2.0.21, JDK 21, Ktor 3.0.3 (CIO), Exposed ORM, Koin DI, gRPC + protobuf, RabbitMQ, Redis (Lettuce), AWS S3. Dependency versions managed in `gradle/libs.versions.toml`.

Four layers: `api/` (gRPC services + REST webhooks) → `application/` (commands/queries, use cases, application ports, events, projections) → `domain/` (models, value objects, factories, repositories, exceptions, events) → `infrastructure/` (adapters, persistence, aggregators, messaging).

### Package layout

```
domain/
├── model/         # Aggregates, entities
├── vo/            # Value objects (@JvmInline value class with init validation)
├── service/       # Domain services (SpinBalanceCalculator, factories)
├── repository/    # Repository INTERFACES (DDD-pure: contracts live with the model)
├── exception/     # Sealed DomainException hierarchy
└── util/          # Trait interfaces (Activatable, Imageable, Orderable)
                   # (events are NOT a domain concern — they live in the top-level `event/` package)

event/             # Central event package (crm envelope pattern)
├── AppEvent.kt    # AppEvent + Meta + AppEventPublisher + AppEventConsumer + appJson + EVENT_EXCHANGE
├── SpinEvent.kt   # one file per event (SpinEvent/RoundEvent/SessionEvent), companion Meta
├── model/         # @Serializable wire snapshots (Spin+SpinType, Round, Session)
└── mapper/        # domain.X.toModel() — domain aggregate → wire snapshot

application/
├── Bus.kt                 # CQRS bus contract
├── IHandler.kt            # CqrsHandler marker, ICommand, IQuery, ICommandHandler, IQueryHandler
├── HandlerRegistry.kt     # Reflective registry — auto-discovery
├── command/<feature>/     # Write-side: command DTOs (no handlers — those live in infra)
├── query/<feature>/       # Read-side: query DTOs + their View result types side-by-side
├── usecase/               # Application services / use cases (orchestrators)
├── port/external/         # Driven ports for external systems (FileAdapter, IWalletPort, IPlayerLimitPort, ...)
└── port/factory/          # Driven ports for adapter factories (AggregatorAdapterProvider, IAggregatorFactory)

infrastructure/
├── handler/<feature>/     # Command/query handler IMPLEMENTATIONS (touch DB / repos / external)
├── persistence/           # Exposed repositories implementing domain.repository contracts
├── aggregator/<vendor>/   # Aggregator integration adapters
├── rabbitmq/              # Event publisher + consumers
├── redis/                 # Player limit cache
├── s3/                    # File storage adapter
├── wallet/                # Wallet gRPC client
└── koin/                  # DI module wiring

api/
├── grpc/service/          # gRPC service implementations (call Bus)
├── grpc/mapper/           # Proto ↔ domain mappers
└── rest/                  # REST webhook routes
```

**Key DDD invariants:**
- **Repository interfaces live in `domain/repository/`** — they are part of the ubiquitous language and the domain model. Implementations are in `infrastructure/persistence/repository/`. The application layer never depends on infrastructure for write paths; it depends on the domain port.
- **Commands and queries are pure data classes** in `application/command/<feature>/` and `application/query/<feature>/`. They contain no logic — all behavior lives in their handler in `infrastructure/handler/<feature>/`.
- **Query result types live next to the query** as top-level data classes in the same file (e.g. `CollectionView` in `FindCollectionQuery.kt`, `LastWin` in `LastWinnerQuery.kt`). When a single read shape is shared across `Find` and `FindAll` for the same feature, the `Find*Query.kt` file owns the type and `FindAll*Query.kt` references it via the same package — there is no separate `projection/` package.
- **Use cases** in `application/usecase/` are orchestrators that take domain models, call repositories + ports, and publish domain events. They are called from command handlers (via `bus.invoke(...)` or directly).

Ports: HTTP 8080 (dev) / 80 (Docker), gRPC 5050. Configurable via `HTTP_PORT` and `GRPC_PORT` env vars.

## Entrypoints

### Main Server (Main.kt)

Boot sequence (each step is a `configure*()` extension function on `Application`):
1. `configureKoin()` — registers `Application` instance, then installs 8 modules (config → persistence → external → usecase → handler → bus → aggregator → grpc)
2. `configureDatabase()` — initializes Exposed connection pool, creates tables
3. `configureSerialization()` — kotlinx.serialization JSON with `ignoreUnknownKeys`
4. `configureCallLogging()`
5. `configureRabbitMq()` — installs RabbitMQ plugin
6. `configureWebhook()` — registers all inbound aggregator webhook routes under `/api/webhook` (OneGameHub, Pragmatic, TONGame) on the shared Ktor HTTP server (defined in `api/webhook/WebhookModule.kt`). New aggregators add their routes here.
7. `configureGrpc()` — launches gRPC server on separate coroutine (IO dispatcher) with 6 services (defined in `api/grpc/GrpcModule.kt`)
8. `configureConsumers()` — starts RabbitMQ event consumers

Environment variables: `HTTP_PORT` (default 8080), `GRPC_PORT` (default 5050).

### Sync Job (SyncJob.kt)

Standalone CLI entrypoint that syncs games from all active aggregators, then exits. Uses `startKoin` directly (not koin-ktor) with same modules minus `grpcModule`, no `Application` registration. Dispatches `SyncAllActiveAggregatorCommand` via the CQRS Bus.

**Important**: SyncJob does NOT register `Application` in Koin. A `syncOverrideModule` is loaded after `externalModule` to replace `AppEventPublisher` with `NoOpAppEventPublisher` (sync doesn't publish events and must not open a RabbitMQ channel). If adding new singletons that depend on `Application` or the RabbitMQ `Channel`, ensure the sync code path doesn't resolve them, or add an override in `syncOverrideModule`.

## CQRS Pattern

`Bus` dispatches via `BusImpl` → `HandlerRegistry`. The registry is populated **automatically** at boot: Koin's `getAll<CqrsHandler>()` surfaces every handler, and `HandlerRegistry.register` pulls the `C`/`Q` generic type argument via Kotlin reflection. Polymorphic handlers (e.g. `SetImageCommandHandler : ICommandHandler<SetImageCommand, Unit>`) also serve concrete subtypes — lookup walks the class hierarchy when an exact match is missed, and the result is cached per concrete class.

**All write paths go through repositories.** Every `SaveXCommandHandler` loads the FK parent (e.g., `IProviderRepository.findByIdentity`), builds/merges a domain aggregate, and calls `repository.save(...)`. There are no `Exposed Table` writes inside handlers — direct writes are confined to `infrastructure/persistence/repository/*Impl`. Repository **interfaces** are defined in `domain/repository/`; **implementations** in `infrastructure/persistence/repository/`.

**Key difference**: Commands return `Result<R>` (wrapped in `runCatching`). Queries return `R` directly.

**Adding a new command/query**:
1. Define the DTO in `application/command/<feature>/X.kt` or `application/query/<feature>/X.kt`
2. Create the handler in `infrastructure/handler/<feature>/XHandler.kt`
3. Bind it with `single(named("x")) { XHandler(...) } bind CqrsHandler::class` in `HandlerModule`

That's it — `BusModule` never needs to be edited.

**Exception helpers**: `domainRequireNotNull(value) { ExceptionType() }` and `domainRequire(condition) { ExceptionType() }` throw categorized `DomainException` subclasses. Handlers and repositories **must not** throw `IllegalArgumentException` or raw `error(...)` for business rule violations — always pick an appropriate `DomainException` subclass so the gRPC interceptor maps to the right status code.

## Data Flow — Spin Lifecycle

1. **OpenSessionUsecase** — aggregator creates game adapter → gets launch URL via `getLaunchUrl(session, lobbyUrl)` → saves session → publishes `SessionEvent(session.toModel())` via `AppEventPublisher`
2. **ProcessSpinUsecase** — for each spin (PLACE/SETTLE/ROLLBACK):
   - Freespin rounds skip balance calculation entirely
   - Regular rounds: check player limits → calculate balance via `SpinBalanceCalculator` → withdraw/deposit via `IWalletPort` → save spin → publish `SpinEvent(spin.toModel())` (lifecycle carried by `Spin.type`, not separate event types)
3. **FinishRoundUsecase** — `round.finish()` returns the finished `Round` → save → publish `RoundEvent(round.toModel())` (with `finished = true`)

Use cases are callable via `operator fun invoke()`, return `Result<Response>`, and take domain models (not DTOs). They inject `AppEventPublisher` and publish per-model snapshot events after the write commits.

**Session convenience**: `session.openRound(externalId, freespinId)` is the preferred way to create a round — it delegates to `RoundFactory` but keeps the call anchored to the parent aggregate.

**Session-command contract**: the wallet/spin entry points take a resolved `Session`, not a token. A webhook first calls `FindSessionQuery(token)` (→ `Session`, throws `SessionNotFoundException`), then dispatches `PlaceSpinSessionCommand(session, …)` / `SettleSpinSessionCommand(session, …)` / `EndRoundSessionCommand(session, …)` / `FindSessionBalanceQuery(session)`. This lets a caller override session fields per operation — e.g. TONGame, where currency isn't session-locked, passes `session.copy(currency = requestCurrency)`. The spin handlers re-bind a DB-loaded round's session to the passed one (`round.copy(session = session)`) so `ProcessSpinUsecase` uses the caller's currency. Other aggregators pass the resolved session unchanged, so behavior is identical.

## Persistence

Exposed ORM wrapped by two helpers in `infrastructure/persistence/DbTransaction.kt`:

- `dbTransaction { }` — suspended write transaction (preferred over `newSuspendedTransaction` direct)
- `dbRead { }` — read-only transaction for query handlers and `find*` repository methods

Nothing outside `DbTransaction.kt` should import `newSuspendedTransaction` directly.

Entity ↔ domain conversion via mapper extension functions (`object XMapper { fun XEntity.toDomain(): X }`). ResultRow extensions use distinct names (`toProvider`, `toAggregator`, etc.) to avoid `toDomain` collisions when one mapper composes another — see `.claude/rules/mapper-conventions.md`.

- **Long PK tables** (`LongIdTable`): SessionTable, RoundTable, SpinTable, GameTable, GameVariantTable, ProviderTable, AggregatorTable, CollectionTable, GameCollectionTable, GameFavouriteTable
- **New entity detection**: `id == Long.MIN_VALUE` means unsaved
- **JSON columns**: `config`, `tags`, `images`, `locales`, `platforms`, `name` (LocaleName) via `kotlinx.serialization`

Repository methods raise domain exceptions on FK violations (`ProviderNotFoundException`, `AggregatorNotFoundException`, `GameNotFoundException`, `CollectionNotFoundException`) via `domainRequireNotNull`. Image updates flow through `IGameRepository.addImage(identity, key, url)` (and the analogous methods on provider/collection) — handlers do not touch entity DAOs directly.

See `.claude/rules/exposed-database.md` for detailed Exposed ORM conventions.

## Proto / gRPC

Proto files in `src/main/proto/game/v1/`. Package: `game.v1` (Java: `com.nekgamebling.game.v1`).

- DTOs in `dto/` subdirectory as `<name>.dto.proto` (see `.claude/rules/proto-dto.md`)
- Services in `service/` subdirectory: GameService, CollectionService, ProviderService, AggregatorService, FreespinService, WinnerService
- Full gRPC client API reference: `src/main/proto/API.md`

Each gRPC service extends `*GrpcKt.*CoroutineImplBase`, takes `Bus` as constructor parameter, and wraps every method in `handleGrpcCall { }` which maps `DomainException` → gRPC status codes and stores the exception class name in an `x-exception-name` metadata header for downstream error identification.

**Exception → Status mapping**: `NotFoundException` → `NOT_FOUND`, `BadRequestException` → `INVALID_ARGUMENT`, `ConflictException` → `ALREADY_EXISTS`, `ForbiddenException` → `PERMISSION_DENIED`, `SystemException` → `INTERNAL`.

**Name collision**: Proto and CQRS classes share names (e.g., `SaveGameCommand`). Use Kotlin import aliases:
```kotlin
import com.nekgamebling.game.v1.SaveGameCommand as SaveGameProto
import application.cqrs.game.SaveGameCommand as SaveGameCqrs
```

## Aggregator Integration

Currently implemented: **OneGameHub** (`infrastructure/aggregator/onegamehub/`), **Pragmatic Play** (`infrastructure/aggregator/pragmatic/`), **Pateplay** (`infrastructure/aggregator/pateplay/`), and **TONGame** (`infrastructure/aggregator/tongame/`).

Routing is handled by `AggregatorRegistry : IAggregatorFactory` (in `infrastructure/aggregator/`). It indexes every bound `AggregatorAdapterProvider` by its `integration` string and raises `AggregatorNotSupportedException` for unknown keys. Each aggregator provides: Config, `*AdapterProvider` (implementing `AggregatorAdapterProvider` — replaces the old `*AdapterFactory`), GameAdapter (IGamePort), FreespinAdapter (IFreespinPort), HttpClient, and Webhook.

**Adding a new aggregator is one new file + one Koin line.** Create `<Name>AdapterProvider` with an `integration` string and factory methods, then in `ExternalModule`:
```kotlin
single(named("<name>")) { <Name>AdapterProvider() } bind AggregatorAdapterProvider::class
```
The registry picks it up through `getAll<AggregatorAdapterProvider>()` at boot — no edits to `AggregatorRegistry` or any existing code.

See `.claude/skills/add-aggregator.md` for the step-by-step guide. See `.claude/agents/seed-collections.md` for the collection seeding agent.

**Pragmatic specifics**: Uses MD5 hash authentication (sorted params + secret key), form-encoded POST requests, GET webhook endpoints at `/pragmatic/*.html`, and decimal string amounts (converted to/from minor units via ×100).

**Pateplay specifics**: Static game catalog (no game discovery API), launch URLs constructed locally (no API call), HMAC-SHA256 authentication for freespin API, no webhook handler (wallet callback not yet implemented).

**TONGame specifics**: REST aggregator (HTTP/JSON, no gRPC/protobuf). Two directions:

- **Outbound (we → provider)**: `TongameHttpClient` (`infrastructure/aggregator/tongame/client/`, Ktor CIO, `expectSuccess=true`) calls `GET <apiUrl>/api/v1/games` and `POST <apiUrl>/api/v1/session` with `X-Operator` + `X-Secret-Key` headers. `getAggregatorGames()` maps the game list (provider supplies only `identity`; `name` defaults to `identity`, locales/platforms defaulted) → `AggregatorGame`. **We mint the session token; the provider mints nothing**: `getLaunchUrl()` calls `POST /api/v1/session {token}` sending our own `session.token`, and embeds that same token in the launch URL. The provider then calls our `/player` webhook with the token to learn the player, and echoes the token back as `sessionToken` in every wallet webhook, so each resolves via our `findByToken`. Freespins unsupported.
- **Inbound (provider → us)**: aggregator **webhook** like `OneGameHubWebhook`/`PragmaticWebhook`. `TongameWebhook` (`infrastructure/aggregator/tongame/webhook/`) exposes six flat POST routes under `/api/webhook/tongame` — `/player`, `/balance`, `/round/open`, `/round/close`, `/debit`, `/credit` — bound in `aggregatorModule` and wired into `api/webhook/WebhookModule.kt`'s `configureWebhook()` (all webhooks share the Ktor HTTP server; **no separate gRPC server**). The registered `webhookUrl` is `<our-host>/api/webhook/tongame`. Every request carries `sessionToken` = our own `session.token`, so each route resolves **our** session via `FindSessionQuery(sessionToken)` (findByToken) and verifies the `X-Secret-Key` header against the aggregator's stored `apiKey` (read off `session.gameVariant.game.provider.aggregator.config`). It then bridges into the spin pipeline via `Bus`: `/player`→resolve session→`IPlayerPort.findPlayer(session.playerId)` (pam-engine `AccountService.FindUser`) → `{id, username, profilePic}` (`id` = our `session.playerId`, which the provider stores as its `player.externalId`), `/balance`→`IWalletPort.findBalance(session.playerId, currency)` → `{balance}`, `/debit`→`PlaceSpinSessionCommand`, `/credit`→`SettleSpinSessionCommand`, `/round/close`→`EndRoundSessionCommand`, `/round/open`→`200` (round opens lazily on first `/debit`). **Currency is not locked to the session for TONGame** — the player can switch currency in-game, so every wallet call carries its own `currency`. The webhook pins it onto the resolved session (`session.copy(currency = …)`) before dispatching (see the session-command contract below). Spin ids are synthesized from `roundId` (`<roundId>:place` / `:settle`). Money on the wire is **integer minor units == the wallet's system units (nano)**, passed straight through (no `ICurrencyPort` conversion). Domain exceptions map to HTTP status: `SessionNotFound`/bad `X-Secret-Key`→`401`, `InsufficientBalance`/`MaxPlaceSpin`→`402` (the `/debit` decline path), `Forbidden`/`NotFound`/`Conflict`→`409`; anything else propagates (→500).

Launch URLs put the game in a subdomain (`<gameSymbol>.<gameHost>`) and carry the three query params the provider's game client reads: `?sessionToken=<our-token>&currency=<currency>&operator=<operatorIdentity>` (the client replays `sessionToken` + `operator` in its WS `auth` frame so the provider resolves our session by `(token, operator)`; no `mode` param). **TONGame has no demo mode** — `getDemoUrl` throws `DemoNotSupportedException` (games are published with `demoEnable=false`). Config keys: `apiUrl` (provider REST base, e.g. `https://provider.example.com`), `operatorIdentity` (sent as `X-Operator`), `apiKey` (the shared secret — sent as `X-Secret-Key` and verified on inbound webhooks), `gameHost`. Player profiles for `/player` come from pam-engine over gRPC (`PamAdapter`/`IPlayerPort`, env `PAM_GRPC_HOST`/`PAM_GRPC_PORT`).

## Event System (AppEvent envelope → RabbitMQ)

**Uniform envelope, per-model events.** Every event ships as `{ "playerId": <key>, "data": {<model>} }` on a `<domain>.events` route. There is one event per domain model carrying the full snapshot — lifecycle lives *inside* the model (e.g. `Spin.type`), never as separate `*Placed`/`*Settled` event types. The central package is top-level `event/`:

- `event/AppEvent.kt` — the entire core: the `AppEvent<T>` interface + `Meta<T>` companion contract, the generic `AppEventPublisher` (owns serialization + channel), the generic `AppEventConsumer<E>` base, the shared `appJson`, the `EVENT_EXCHANGE` env constant (`EVENT_EXCHANGE` env, default `crm.exchange`), and `declareEventExchange(channel)`.
- `event/SpinEvent.kt` / `RoundEvent.kt` / `SessionEvent.kt` — one file per event. Each has exactly one `data` param, derives `playerId` from `data`, and a companion `Meta` supplying `route` + `serializer` + `create(data)`.
- `event/model/` — `@Serializable` wire snapshots `Spin` (+ `SpinType{Place,Settle,Rollback}`), `Round`, `Session`. These are twins of the domain aggregates, never the aggregates themselves.
- `event/mapper/` — `domain.X.toModel()` extensions producing the wire snapshot at publish time (pulling `playerId`/`gameIdentity`/`currency`/`freespinId` out of the nested `Spin.round.session` graph).

**Routes:** `spin.events`, `round.events`, `session.events`. The casino→bonus stream is a hard cross-engine contract — see the wire shapes in the bonus-engine docs; `SpinType` serializes to exactly `Place`/`Settle`/`Rollback`, and round-finished is `RoundEvent` with `finished = true` (NOT a spin type).

**No codec outside the core.** Use cases inject `AppEventPublisher` and call `publish(SpinEvent(spin.toModel()))` etc. The publisher wraps the snapshot in the envelope and publishes on `meta.route`. No use case / event / consumer touches JSON, bytes, or the channel.

**Consumer**: `PlaceSpinEventConsumer : AppEventConsumer<SpinEvent>(channel, SpinEvent::class)` is a read-only router — its `handle()` only checks `spin.type == Place` and delegates the Redis limit decrement to `DecreasePlayerLimitUsecase` (which owns `IPlayerLimitPort`). The consumer holds no business logic and never publishes. Its queue name is auto-derived from the class `simpleName`; the base `init` declares the queue, binds it to `EVENT_EXCHANGE` on `spin.events`, and starts consuming — so resolving the consumer from Koin is enough to start it. The AMQP delivery callback decodes the envelope and runs `handle()` via `runBlocking` (mirroring the crm reference). Auto-ack is on; a failed `handle()` is logged, not requeued.

**Channel**: `infrastructure/rabbitmq/rabbitMqChannel(config)` opens a single `com.rabbitmq.client.Channel` from `RabbitMqConfig`; it backs both the publisher and every consumer (bound as `single<Channel>` in `ExternalModule`).

**Publishing timing**: usecases publish **after** the DB transaction commits (outside the `dbTransaction { }` block) so a failed write never emits phantom events. See `.claude/rules/domain-events.md`. `ProcessSpinUsecase` dispatches the wallet debit/credit fire-and-forget through `IBackgroundTaskPort` (`BackgroundWorker` catches and logs any wallet failure) and publishes `SpinEvent` once the spin row commits — a failed wallet move is reconciled out-of-band and does NOT suppress or roll back the published event. The committed spin is the source of truth.

**Publisher null-channel**: `AppEventPublisher.publish` throws `EventPublishingException` (a `SystemException`) when its channel is null, so the gRPC interceptor maps it to `INTERNAL` instead of letting a raw `IllegalArgumentException` escape. `NoOpAppEventPublisher` (sync job) overrides `publish` to a no-op, so it never hits this path.

**Connection config**: `RabbitMqConfig` (built in `infrastructure/koin/ConfigModule.kt`) reads `RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USER`, `RABBIT_PASSWORD`, and `RABBIT_TLS` from env. `RABBIT_TLS=true` switches the URI scheme to `amqps://` — required for AWS Amazon MQ for RabbitMQ and any TLS-only broker. The Java client auto-enables TLS via the URI scheme using the JVM's default trust store (publicly-signed CAs), so no keystore is needed for AWS. Set `RABBIT_PORT=5671` alongside `RABBIT_TLS=true`; the default port is not changed automatically.

## Koin Dependency Injection

**Module install order matters** — dependencies must be installed before dependents.

**Main server** (`infrastructure/koin/KoinInit.kt`): Registers `Application` instance first, then 8 modules:
`configModule → persistenceModule → externalModule → usecaseModule → handlerModule → busModule → aggregatorModule → grpcModule`

The `grpcModule` is defined in `api/grpc/config/` and registers gRPC service singletons. All other modules are in `infrastructure/koin/`.

**`HandlerModule`**: every handler is declared as `single(named("<x>")) { ... } bind CqrsHandler::class`. The named qualifier is required because Koin rejects duplicate `single`s of the same type when binding to a common supertype; the marker binding is what allows `busModule` to `getAll<CqrsHandler>()` in one call.

**`BusModule`**: tiny (~13 lines). It constructs a `HandlerRegistry`, populates it from `getAll<CqrsHandler>()`, and wraps the result in `BusImpl`. Never needs to be touched when adding handlers.

**`ExternalModule`**: `AggregatorAdapterProvider`s are bound with named qualifiers and `bind AggregatorAdapterProvider::class` so `AggregatorRegistry(providers = getAll())` collects them all. It also binds `single<Channel> { rabbitMqChannel(get()) }`, `single { AppEventPublisher(channel = get()) }`, and the `PlaceSpinEventConsumer`. (Note: there is no `ImageAttachmentService` — `SetImageCommandHandler` calls `FileAdapter.upload(...)` directly.)

**SyncJob** (SyncJob.kt): Same modules minus `grpcModule`, no `Application` registration, includes `syncOverrideModule` which binds `single<AppEventPublisher> { NoOpAppEventPublisher }` so sync never publishes events or opens a RabbitMQ channel.

**Application registration**: the `Application` instance is registered in `configureKoin()` as `module { single { application } }` (koin-ktor does not auto-register it) for the webhook/gRPC layers. The event publisher and consumer no longer depend on `Application` — they take a `com.rabbitmq.client.Channel`.

## Key Design Decisions

- **Value objects**: `@JvmInline value class` with `init` block validation via `domainRequire(...)`; validation errors are `DomainException` subclasses, not `IllegalArgumentException`
- **Amount**: wraps `Long` in minor units (cents) with operator overloads; `Amount.ZERO` constant; `minOf(Amount, Amount)` top-level helper
- **Domain traits** (Activatable, Imageable, Orderable): mutable interfaces. Game overrides via `copy()` for immutability; Provider/Collection/Aggregator mutate directly
- **Monetary values**: `Long` in minor units internally, `string` in proto for BigInteger precision
- **Factories**: `object` singletons with validation (e.g., `SessionFactory.create()` checks active status and locale/platform support); `Session.openRound()` delegates to `RoundFactory` as a convenience on the parent aggregate
- **SpinBalanceCalculator**: PLACE deducts (real-first when bonusBet), SETTLE deposits to same pool as original bet, ROLLBACK refunds to original pools. Pre-checks `canAfford` for every spin type. Exhaustively unit-tested.
- **Spin convenience**: `spin.isPlace` / `isSettle` / `isRollback` computed properties. The wire snapshot is built by `domain.Spin.toModel()` (`event/mapper/`), which maps `SpinType.PLACE/SETTLE/ROLLBACK` → wire `Place/Settle/Rollback`
- **Round.finish()**: returns the finished `Round` (sets `finishedAt`); `FinishRoundUsecase` publishes `RoundEvent(round.toModel())` with `finished = true` after the write commits
- **Read-side projections**: query handlers that join across aggregates return `application/projection/<ctx>/<X>Projection` DTOs (e.g. `CollectionProjection` with game counts), never polluting domain models with denormalized fields
- **Wallet dependency**: wallet proto resolved via direct `srcDir("../wallete-engine/proto")` source reference in `build.gradle.kts` (note the "wallete" spelling — intentional carve-out, do NOT fix)
- **File storage interface**: Named `FileAdapter` (not `FilePort`), located in `application/port/external/FilePort.kt` — intentional carve-out, do NOT rename

## CI/CD

GitHub Actions workflow (`publish-grpc-client.yml`) publishes `com.nekgamebling:game-grpc-client` to GitHub Packages on tag push (`v*`) or manual dispatch. Version can be overridden with `-PgrpcClientVersion=x.y.z`.
