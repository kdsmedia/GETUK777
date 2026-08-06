# Architecture

Casino Engine follows **hexagonal architecture** (ports and adapters) with
**CQRS** for catalog management and **domain-driven design** for the
betting model.

## Layered view

```
┌─────────────────────────────────────────────────────────────────────┐
│                       API Layer (gRPC + REST)                       │
│  ┌────────────────────────┐  ┌──────────────────────────────────┐   │
│  │  gRPC Services (5)     │  │  REST Webhooks (Aggregators)     │   │
│  └────────────────────────┘  └──────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────────┤
│                       Application Layer                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌───────────┐   │
│  │  Use Cases  │  │  CQRS Bus   │  │   Events    │  │  Handlers │   │
│  └─────────────┘  └─────────────┘  └─────────────┘  └───────────┘   │
├─────────────────────────────────────────────────────────────────────┤
│                         Domain Layer                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌───────────┐   │
│  │  Entities   │  │Value Objects│  │  Services   │  │   Errors  │   │
│  └─────────────┘  └─────────────┘  └─────────────┘  └───────────┘   │
├─────────────────────────────────────────────────────────────────────┤
│                      Infrastructure Layer                           │
│  ┌────────────────┐  ┌──────────────┐  ┌───────────────────────┐    │
│  │   Aggregators  │  │  Persistence │  │  Adapters             │    │
│  │  (Pragmatic,   │  │  (Exposed    │  │  - WalletAdapter      │    │
│  │   OneGameHub,  │  │   ORM)       │  │  - PlayerLimitAdapter │    │
│  │   Pateplay)    │  │              │  │  - PamAdapter         │    │
│  └────────────────┘  └──────────────┘  └───────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
```

The domain layer has no dependencies on application or infrastructure.
The application layer depends only on domain and on port interfaces. All
external concerns (DB, RabbitMQ, Redis, HTTP clients for aggregators)
live in infrastructure and implement port interfaces.

## Source structure

```
src/main/kotlin/
├── api/
│   ├── grpc/           # gRPC service implementations, mappers, interceptors
│   └── rest/           # Aggregator webhook REST endpoints
├── application/
│   ├── cqrs/           # Commands, Queries, Bus (organised by domain)
│   ├── event/          # Domain events (SessionOpen, Spin, RoundEnd)
│   ├── port/           # Port interfaces: storage/, external/, factory/
│   └── usecase/        # Orchestrators: OpenSession, ProcessSpin,
│                       #                FinishRound, SyncAggregator
├── domain/
│   ├── exception/      # DomainException hierarchy
│   ├── model/          # Aggregates: Game, Session, Round, Spin,
│   │                   #             Provider, Collection, Aggregator
│   ├── service/        # Factories: SessionFactory, RoundFactory,
│   │                   #            SpinFactory, SpinBalanceCalculator
│   ├── util/           # Mutable traits: Activatable, Imageable, Orderable
│   └── vo/             # Value objects: Identity, Currency, Locale,
│                       #                Amount, PlayerId, SessionToken
└── infrastructure/
    ├── aggregator/     # Aggregator adapters (OneGameHub, Pragmatic, Pateplay)
    ├── handler/        # CQRS handler implementations
    ├── koin/           # DI modules (8 ordered modules)
    ├── persistence/    # Exposed ORM: tables, entities, mappers, repositories
    ├── rabbitmq/       # Event publisher + consumer + event mappers
    ├── redis/          # PlayerLimitRedis
    └── wallet/         # WalletAdapter (gRPC client to wallet-service)
```

## Entrypoints

| Entrypoint | Command | Purpose |
| --- | --- | --- |
| Main server | `/app/bin/casino-engine` | HTTP + gRPC + RabbitMQ consumers |
| Sync job | `/app/bin/sync-aggregators` | One-shot game sync from all active aggregators |

## Deployment topology

Casino Engine is a **private service**. It should not be exposed
directly to the public internet. The expected deployment is:

```
┌──────────┐      ┌────────────────┐      ┌─────────────────┐
│  Client  │ ───► │  Your Public   │ ───► │  Casino Engine  │
│          │ ◄─── │  API Gateway   │ ◄─── │  (gRPC :5050)   │
└──────────┘      └────────────────┘      └─────────────────┘
                         │
                         ▼
                  ┌─────────────┐
                  │ Auth / Rate │
                  │ Limiting    │
                  └─────────────┘
```

Your public-facing decorator handles:

1. **Authentication** — JWT, API keys, session cookies
2. **Authorisation** — player access control, jurisdiction rules
3. **Rate limiting** — per-player, per-IP, per-session
4. **Logging and monitoring** — traffic, errors, latency
5. **Response transformation** — adapt gRPC to your client format (REST, GraphQL, WebSocket)

Aggregator webhooks (Pragmatic, OneGameHub, Pateplay) are an exception —
those must be reachable from the aggregator's network and are
authenticated via aggregator-specific signature schemes.

## Session flow

```
┌──────────┐    1. Play(game, player)    ┌─────────────┐    2. Get Launch URL    ┌────────────┐
│  Client  │ ────────────────────────────►│ Casino      │ ───────────────────────►│ Aggregator │
│          │ ◄────────────────────────────│ Engine      │ ◄─────────────────────── │            │
└──────────┘    4. Launch URL            └─────────────┘    3. Launch URL        └────────────┘
     │                                         │
     │         5. Launch Game                  │
     └─────────────────────────────────────────┼────────────────────────────────────────►
                                               │
                                               │  6. Save Session + Publish Event
                                               ▼
                                    ┌────────────────────┐
                                    │ Database + RabbitMQ │
                                    └────────────────────┘
```

## Betting flow

```
┌────────────┐   1. Bet Callback      ┌─────────────┐   2. Find Session
│ Aggregator │ ──────────────────────►│ Webhook     │ ──────────────────►
│            │ ◄──────────────────────│ Handler     │
└────────────┘   6. Balance Response  └─────────────┘
                                            │
                                            │ 3. ProcessSpin
                                            ▼
                                      ┌──────────────┐    4. Withdraw/Deposit
                                      │ProcessSpin   │ ──────────────────────►
                                      │Usecase       │          ┌─────────────┐
                                      └──────────────┘          │ IWalletPort │
                                            │                   └─────────────┘
                                            │ 5. Publish Event
                                            ▼
                                      ┌─────────────┐
                                      │  RabbitMQ   │
                                      └─────────────┘
```

## Round lifecycle

1. **First bet** — round created with `extId` from aggregator
2. **Additional bets** — same round reused (matched by `extId`)
3. **Settle** — win/loss recorded, funds deposited to wallet
4. **End round** — round marked as finished
5. **Rollback** — previous spin reversed, funds refunded to original pools

## Balance calculation

`SpinBalanceCalculator` handles the real/bonus split:

| Spin Type | Real Balance | Bonus Balance |
| --- | --- | --- |
| **PLACE** | Deducts real amount (real-first when bonusBet) | Deducts bonus amount |
| **SETTLE** | Deposits to same pool as original bet | Deposits to same pool as original bet |
| **ROLLBACK** | Refunds to original pool | Refunds to original pool |

Freespin rounds skip balance checks entirely — wallet operations are
bypassed.

## Use cases

### Session management

| Use case | Description |
| --- | --- |
| `OpenSessionUsecase` | Opens a new game session — creates adapter, gets launch URL from aggregator, saves session, publishes event |

### Spin (betting) operations

All spin operations go through `ProcessSpinUsecase`:

| Spin type | Flow |
| --- | --- |
| **PLACE** | Check player limits → calculate balance (real-first deduction) → withdraw from wallet → save spin → publish event |
| **SETTLE** | Calculate win amounts → deposit to wallet (same pool as original bet) → save spin → publish event |
| **ROLLBACK** | Refund to original pools → save rollback spin → publish event |

### Round management

| Use case | Description |
| --- | --- |
| `FinishRoundUsecase` | Marks round as finished, publishes `RoundEndEvent` |

### Game sync

| Use case | Description |
| --- | --- |
| `SyncAggregatorUsecase` | Syncs games from an aggregator — fetches game list, creates/updates providers, games, and variants |

## CQRS handlers

Beyond use cases, catalog management goes through CQRS handlers. See
[API.md](./API.md) for the full list of commands and queries.

Categories:

- **Game management** — Save, Find, FindAll, Batch, UpdateImage, Play,
  OpenDemo, GameFavourite
- **Collection management** — Save, Find, FindAll, UpdateGames, UpdateImage
- **Provider management** — Save, Find, FindAll, UpdateImage
- **Aggregator management** — Save, Find, FindAll, SyncAllActive
- **Freespin management** — GetPreset, Create, Cancel

## Event system

Domain events are published to RabbitMQ. Subscribe to these for analytics,
notifications, or downstream processing.

| Event | Routing key | Description |
| --- | --- | --- |
| `SessionOpenEvent` | `session.opened` | New game session created |
| `SpinEvent` (PLACE) | `spin.placed` | Bet was placed |
| `SpinEvent` (SETTLE) | `spin.settled` | Spin result settled (win/loss) |
| `SpinEvent` (ROLLBACK) | `spin.rollback` | Bet was refunded |
| `RoundEndEvent` | `round.finished` | Round was closed |
| `GameFavouriteAdded` | `game.favourite.added` | Game added to favourites |
| `GameFavouriteRemoved` | `game.favourite.removed` | Game removed from favourites |
| `GameWon` | `game.won` | Win recorded |

### Built-in consumer

`PlaceSpinEventConsumer` subscribes to `spin.placed` events and updates
player betting limits via `IPlayerLimitPort` (Redis).

## Technology choices

| Component | Choice | Why |
| --- | --- | --- |
| Kotlin 2.0 / JVM 21 | Modern, expressive, runs on the JVM | Best ergonomics for domain modelling with sealed classes, value classes, and coroutines |
| Ktor 3.0 (CIO) | Lightweight, coroutine-native HTTP | Lower overhead than Spring for webhook routes; pairs naturally with the rest of the Kotlin stack |
| Exposed ORM | Kotlin-native SQL DSL | Type-safe queries without the heavyweight magic of JPA |
| gRPC + Protobuf | Strongly-typed RPC | Versioned `game.v1` package, generated client JARs for downstream consumers |
| RabbitMQ | Mature message broker | Reliable event delivery with routing keys and DLQs |
| Redis (Lettuce) | Coroutine-friendly Redis client | Player limit TTLs, atomic ops via Lua scripts |
| Koin | Lightweight DI for Kotlin | No annotations, no proxies, 8 ordered modules for explicit wiring |
| PostgreSQL 16 | Battle-tested relational DB | Strong consistency guarantees for financial data |

See [`CONFIGURATION.md`](./CONFIGURATION.md) for environment variables and
infrastructure ports.
