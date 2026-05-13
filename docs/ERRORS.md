# Error handling

Casino Engine uses typed domain exceptions mapped to gRPC status codes.
The `x-exception-name` metadata header carries the exception class name
for downstream error identification.

## Exception hierarchy

| Category | gRPC status | Exceptions |
| --- | --- | --- |
| `NotFoundException` | `NOT_FOUND` | `SessionNotFoundException`, `RoundNotFoundException`, `GameNotFoundException`, `CollectionNotFoundException` |
| `BadRequestException` | `INVALID_ARGUMENT` | `BlankSessionTokenException`, `BlankLocaleException`, `BlankCurrencyException`, `BlankPlayerIdException`, `InvalidAmountException`, `EmptyIdentityException`, `InvalidIdentityFormatException`, `SpinReferenceRequiredException`, `UnsupportedLocaleException`, `UnsupportedPlatformException` |
| `ConflictException` | `ALREADY_EXISTS` | `RoundAlreadyFinishedException`, `GameNotActiveException`, `ProviderNotActiveException`, `AggregatorNotActiveException`, `FreespinNotSupportedException` |
| `ForbiddenException` | `PERMISSION_DENIED` | `InsufficientBalanceException`, `MaxPlaceSpinException` |
| `SystemException` | `INTERNAL` | Internal / unexpected errors |

## Helper functions

```kotlin
// Throws categorised DomainException if value is null
domainRequireNotNull(value) { GameNotFoundException() }

// Throws categorised DomainException if condition is false
domainRequire(round.isActive) { RoundAlreadyFinishedException() }
```

These keep error handling concise without dropping type information.

## Client-side handling

Downstream services using the published gRPC client JAR can dispatch
on `x-exception-name`:

```kotlin
try {
    gameClient.play(command)
} catch (e: StatusRuntimeException) {
    val exceptionName = e.trailers?.get(
        Metadata.Key.of("x-exception-name", Metadata.ASCII_STRING_MARSHALLER)
    )
    when (exceptionName) {
        "InsufficientBalanceException" -> showDepositPrompt()
        "MaxPlaceSpinException" -> showLimitWarning()
        "GameNotActiveException" -> showAlternativeGames()
        else -> showGenericError()
    }
}
```

The status code alone is often enough — fall back to the exception name
only when you need to distinguish errors that share a status code (e.g.
`InsufficientBalanceException` vs `MaxPlaceSpinException`, both
`PERMISSION_DENIED`).

## Logging

Domain exceptions are expected outcomes — they're logged at `INFO` level
with structured context (player, session, transaction). Only
`SystemException` and uncaught throwables are logged at `ERROR`.

This keeps `ERROR`-level dashboards focused on actual bugs and
infrastructure issues rather than expected business outcomes like
insufficient balance.
