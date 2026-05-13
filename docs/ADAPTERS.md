# Custom adapters

Casino Engine defines a set of port interfaces that you must implement
for production use. The repository ships with reference implementations
(`WalletAdapter`, `PlayerLimitRedis`, `S3FileAdapter`, etc.) that you
can use directly or replace with your own.

## IWalletPort

The most important adapter — handles all real money movement.

**Interface:** `application/port/external/IWalletPort.kt`

```kotlin
interface IWalletPort {
    suspend fun findBalance(playerId: PlayerId, currency: Currency): PlayerBalance

    suspend fun withdraw(
        playerId: PlayerId,
        transactionId: String,
        currency: Currency,
        realAmount: Amount,
        bonusAmount: Amount
    ): PlayerBalance

    suspend fun deposit(
        playerId: PlayerId,
        transactionId: String,
        currency: Currency,
        realAmount: Amount,
        bonusAmount: Amount
    ): PlayerBalance
}
```

**Included implementation:** `WalletAdapter` — gRPC client to the
companion `wallet-service`.

### Idempotency contract

`transactionId` is the idempotency key. Your implementation **must**:

- Return the same result for repeated calls with the same `transactionId`
- Never double-debit or double-credit on retry
- Persist the transaction record before returning success

If you're integrating with an existing wallet service, ensure it
supports idempotent operations or wrap it with an idempotency layer.

## IPlayerLimitPort

Per-player betting limits with TTL.

**Interface:** `application/port/external/IPlayerLimitPort.kt`

```kotlin
interface IPlayerLimitPort {
    suspend fun getMaxPlaceAmount(playerId: PlayerId): Amount?
    suspend fun saveMaxPlaceAmount(playerId: PlayerId, amount: Amount)
}
```

**Included implementation:** `PlayerLimitRedis` — Redis-backed with TTL.

## FileAdapter

Game / provider / collection image storage.

**Interface:** `application/port/external/FileAdapter.kt`

```kotlin
data class MediaFile(
    val ext: String,
    val bytes: ByteArray
)

interface FileAdapter {
    suspend fun upload(folder: String, fileName: String, file: MediaFile): Result<String>
    suspend fun delete(path: String): Result<Boolean>
}
```

**Included implementation:** `S3FileAdapter` — S3 / MinIO-compatible
storage.

## IEventPort

Domain event publisher.

**Interface:** `application/port/external/IEventPort.kt`

```kotlin
interface IEventPort {
    suspend fun publish(event: ApplicationEvent)
}
```

**Included implementation:** `RabbitMqEventPublisher` — publishes
domain events to a RabbitMQ exchange.

See [ARCHITECTURE.md](./ARCHITECTURE.md#event-system) for the list of
events published.

## ICurrencyPort

Minor unit conversion.

**Interface:** `application/port/external/ICurrencyPort.kt`

```kotlin
interface ICurrencyPort {
    suspend fun convertToUnits(amount: Double, currency: Currency): Long
    suspend fun convertFromUnits(amount: Long, currency: Currency): Double
}
```

**Included implementation:** `CurrencyAdapter` — minor unit conversion
(×100 for fiat; configurable for crypto).

Replace this if your currencies have non-standard decimal places (some
crypto tokens use 18 decimals, some stablecoins 6).

## Registering custom adapters

Wire your implementations in the appropriate Koin module
(`externalModule`):

```kotlin
val externalModule = module {
    single<IWalletPort> { YourWalletAdapter(/* dependencies */) }
    single<IPlayerLimitPort> { YourPlayerLimitAdapter(/* dependencies */) }
    single<IEventPort> { YourEventPublisher(/* dependencies */) }
    single<ICurrencyPort> { YourCurrencyAdapter(/* dependencies */) }
    single<FileAdapter> { YourFileAdapter(/* dependencies */) }
}
```

Order matters — the Koin modules are loaded in a defined sequence in
`infrastructure/koin/`. New adapters belong in the `externalModule` slot
or in their own module loaded after `externalModule`.

## Testing adapters

For each adapter, we recommend:

- **Unit tests** for any non-trivial logic (signature generation, amount
  conversion, retry handling)
- **Contract tests** verifying that idempotency holds — call with the
  same `transactionId` twice, assert the same result and no duplicate
  state
- **Integration tests** against a real downstream service in CI (use
  testcontainers for PostgreSQL, Redis, RabbitMQ, MinIO)

`PlayerLimitRedis` and `S3FileAdapter` in this repository are good
starting points for the test pattern.
