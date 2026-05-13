# Aggregator integrations

Casino Engine integrates with game aggregators through a consistent
adapter pattern. This document covers the supported aggregators and how
to add a new one.

## Supported aggregators

| Aggregator | Integration type | Status |
| --- | --- | --- |
| Pragmatic Play | `PRAGMATIC` | Production |
| OneGameHub | `ONEGAMEHUB` | Production |
| Pateplay | `PATEPLAY` | Production (wallet callback handler pending) |

PRs welcome for additional aggregators. See [Adding a new aggregator](#adding-a-new-aggregator)
below, and the [aggregator integration request template](https://github.com/nekzabirov/IGaming-Game-Engine/issues/new?template=aggregator_integration.yml)
for proposing one.

---

## Pragmatic Play

**Integration type:** `PRAGMATIC`

### Configuration

| Key | Description | Required |
| --- | --- | --- |
| `secretKey` | API secret key provided by Pragmatic | Yes |
| `secureLogin` | Secure login identifier | Yes |
| `gatewayUrl` | Pragmatic API gateway URL | Yes |

Example:

```json
{
  "secretKey": "your-secret-key",
  "secureLogin": "your-secure-login",
  "gatewayUrl": "https://api.pragmaticplay.net"
}
```

### Authentication

MD5 hash of sorted query parameters concatenated with the secret key.

### Callback endpoints

GET requests at `/pragmatic/*.html`:

| Endpoint | Purpose |
| --- | --- |
| `/authenticate.html` | Validates session token |
| `/balance.html` | Returns player balance |
| `/bet.html` | Processes bet placement |
| `/result.html` | Processes spin result / win |
| `/bonusWin.html` | Bonus win notification |
| `/jackpotWin.html` | Jackpot win notification |
| `/refund.html` | Refunds a transaction |
| `/endRound.html` | Closes the round |
| `/adjustment.html` | Manual balance adjustment |

### Amount format

Decimal strings, converted to and from minor units (×100) for internal
representation.

---

## OneGameHub

**Integration type:** `ONEGAMEHUB`

### Configuration

| Key | Description | Required |
| --- | --- | --- |
| `salt` | Encryption salt | Yes |
| `secret` | API secret | Yes |
| `partner` | Partner identifier | Yes |
| `gateway` | OneGameHub API gateway URL | Yes |

Example:

```json
{
  "salt": "your-salt",
  "secret": "your-secret",
  "partner": "your-partner-id",
  "gateway": "https://api.onegamehub.com"
}
```

### Callback endpoint

POST at `/onegamehub`. Actions are routed via the `action` query
parameter:

| Action | Purpose |
| --- | --- |
| `balance` | Returns player balance |
| `bet` | Processes bet |
| `win` | Processes win |

Session token is passed via the `extra` query parameter.

---

## Pateplay

**Integration type:** `PATEPLAY`

### Configuration

| Key | Description | Required |
| --- | --- | --- |
| `gatewayUrl` | Pateplay API gateway URL | Yes |
| `siteCode` | Site identifier | Yes |
| `gatewayApiKey` | API key for gateway | Yes |
| `gatewayApiSecret` | API secret for gateway | Yes |
| `gameLaunchUrl` | Base URL for game launch | Yes |
| `gameDemoLaunchUrl` | Base URL for demo games | Yes |
| `walletApiKey` | Wallet API key | Yes |
| `walletApiSecret` | Wallet API secret | Yes |

Example:

```json
{
  "gatewayUrl": "https://api.pateplay.com",
  "siteCode": "your-site-code",
  "gatewayApiKey": "your-api-key",
  "gatewayApiSecret": "your-api-secret",
  "gameLaunchUrl": "https://games.pateplay.com/launch",
  "gameDemoLaunchUrl": "https://games.pateplay.com/demo",
  "walletApiKey": "your-wallet-key",
  "walletApiSecret": "your-wallet-secret"
}
```

### Authentication

HMAC-SHA256 for the freespin API.

### Notes

- Static game catalog — no game discovery API. Launch URLs are
  constructed locally from configuration.
- Wallet callback handler is not yet implemented (contributions welcome).

---

## Adding a new aggregator

Follow these steps to integrate a new aggregator. Reference the existing
adapters in `src/main/kotlin/infrastructure/aggregator/` for working
examples.

### Step 1 — Configuration model

Create `infrastructure/aggregator/<name>/model/YourConfig.kt`:

```kotlin
internal class YourConfig(config: Map<String, String>) {
    val apiKey = config["apiKey"] ?: ""
    val secretKey = config["secretKey"] ?: ""
    val gatewayUrl = config["gatewayUrl"] ?: ""
}
```

### Step 2 — Game adapter (`IGamePort`)

```kotlin
class YourGameAdapter(
    private val aggregator: Aggregator
) : IGamePort {

    override suspend fun getAggregatorGames(): List<AggregatorGame> {
        // Fetch games from aggregator API
    }

    override suspend fun getDemoUrl(
        gameSymbol: String,
        locale: Locale,
        platform: Platform,
        currency: Currency,
        lobbyUrl: String
    ): String {
        // Build demo launch URL
    }

    override suspend fun getLunchUrl(
        session: Session,
        lobbyUrl: String
    ): String {
        // Build real-money launch URL
    }
}
```

### Step 3 — Freespin adapter (`IFreespinPort`)

If the aggregator supports freespins:

```kotlin
class YourFreespinAdapter(
    private val aggregator: Aggregator
) : IFreespinPort {

    override suspend fun getPreset(gameSymbol: String): Map<String, Any> { … }
    override suspend fun create(…) { … }
    override suspend fun cancel(referenceId: String) { … }
}
```

If the aggregator does not support freespins, throw
`FreespinNotSupportedException`.

### Step 4 — Register in `AggregatorFabricImpl`

```kotlin
"YOUR_AGGREGATOR" -> YourGameAdapter(aggregator)
```

### Step 5 — Webhook handler

If the aggregator uses callbacks, add a Ktor route:

```kotlin
fun Route.yourAggregatorRoutes(handler: YourHandler) {
    route("/youraggregator") {
        post("/balance") { … }
        post("/bet") { … }
        post("/win") { … }
        post("/refund") { … }
    }
}
```

### Step 6 — Koin module

Create a Koin module for the new adapters and include it in
`AggregatorModule`. Register webhook routes in `Main.kt`.

### Step 7 — Tests

- Unit tests for hash / signature logic in the config or adapter
- Integration tests against the aggregator's sandbox if credentials
  are available
- Webhook smoke tests using `TestApplicationEngine` if a callback
  handler was added

### Step 8 — Documentation

Add a section to this file documenting:

- Integration type string
- Configuration keys and example
- Authentication scheme
- Callback endpoints and request shapes
- Amount format and any notable quirks

### Submit the PR

Open a PR using the aggregator integration template. Include:

- Link to public aggregator docs (or note NDA status)
- Confirmation you tested against the sandbox
- Any limitations or unsupported features
