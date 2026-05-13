# Contributing to Casino Engine

Thank you for considering a contribution. Casino Engine is the open-source
game-mechanics engine of the [1638.cloud](https://1638.cloud) iGaming
platform, and we welcome contributions from operators, platform engineers,
and anyone working in iGaming infrastructure.

## Ways to contribute

- **Bug reports** — open an [issue](https://github.com/nekzabirov/IGaming-Game-Engine/issues/new/choose)
  with a reproduction
- **Aggregator integrations** — submit a PR adding a new aggregator adapter
  following the pattern in [`docs/INTEGRATIONS.md`](./docs/INTEGRATIONS.md)
- **Documentation improvements** — typos, clarifications, missing examples
- **Performance reports** — benchmarks, profiling data, optimisation
  suggestions from production deployments
- **Feature proposals** — open a [Discussion](https://github.com/nekzabirov/IGaming-Game-Engine/discussions)
  first to align before writing code

## Before you start

For non-trivial changes, **open an issue or discussion first**. We may be
working on the same thing, or have context on why a particular approach
won't fit the architecture. This saves everyone time.

For trivial changes (typos, docs, single-line fixes) — just send a PR.

## Development setup

```bash
git clone https://github.com/nekzabirov/IGaming-Game-Engine.git
cd IGaming-Game-Engine

# Start infra
docker-compose up -d postgres rabbitmq redis minio minio-init

# Build & run
cp .env.example .env
./gradlew run
```

See [`docs/CONFIGURATION.md`](./docs/CONFIGURATION.md) for environment
variables.

## Pull request guidelines

1. **One concern per PR.** Don't mix a refactor with a bug fix with a new
   feature.
2. **Tests required for new logic.** Use cases and adapters need tests.
   Pure DTOs and mappers don't.
3. **Match existing style.** Kotlin idioms, hexagonal layer boundaries,
   no business logic in adapters.
4. **Keep commits clean.** Squash work-in-progress commits before opening
   the PR. Each commit should compile.
5. **Update docs.** If you change behaviour, update the relevant file in
   `docs/`.
6. **No breaking changes to gRPC proto without discussion.** The
   `game.v1` package is consumed by downstream clients. Breaking changes
   need a major version bump and a migration note.

## Code style

- **Kotlin** — follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Architecture** — hexagonal: domain has no dependencies on
  infrastructure or application layers
- **Naming** — `IPort` for port interfaces (`IWalletPort`, `IEventPort`),
  `UseCase` suffix for orchestrators, `Adapter` suffix for adapter
  implementations
- **Errors** — domain errors as sealed exception classes, never silent
  failures
- **Logging** — structured logs only, no `println`
- **Monetary amounts** — always `Long` in minor units (cents, satoshis),
  never `Double` or `BigDecimal` in domain code

## Commit messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(aggregator): add Evolution Gaming adapter
fix(spin): correct bonus-first deduction in rollback
docs(integrations): clarify webhook signature format
refactor(usecase): extract round resolution to factory
```

Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `perf`.

## Adding a new aggregator

Aggregator integrations are the most welcome PRs because each one expands
the engine's reach. The pattern is documented in
[`docs/INTEGRATIONS.md`](./docs/INTEGRATIONS.md). Briefly:

1. Create `infrastructure/aggregator/<name>/` with config model, game
   adapter, and freespin adapter (if supported)
2. Register in `AggregatorFabricImpl`
3. Add webhook routes if the aggregator uses callbacks
4. Wire into Koin module
5. Add integration tests against the aggregator's sandbox
6. Document the configuration keys in `docs/INTEGRATIONS.md`

If you have access to a production aggregator that isn't on our list and
want to contribute the integration — please reach out at
[customer@1638.cloud](mailto:customer@1638.cloud) before starting work.
We may be able to help with API access or sandbox credentials.

## Security

**Never** open a public issue or PR for a security vulnerability. See
[`SECURITY.md`](./SECURITY.md) for the disclosure process.

## Licence

By submitting a contribution, you agree that your contribution will be
licensed under the [Apache 2.0 Licence](./LICENSE), the same licence as
the rest of the project.

You retain copyright of your contribution. You grant 1638.cloud and
downstream users the rights described in Apache 2.0 (use, modify,
sublicense, distribute, including for commercial purposes) and the patent
grant in section 3.

## Code of Conduct

This project follows the [Contributor Covenant](./CODE_OF_CONDUCT.md).
Be respectful. Disagreements are welcome; personal attacks are not.

## Questions

- **General questions** — open a [Discussion](https://github.com/nekzabirov/IGaming-Game-Engine/discussions)
- **Commercial enquiries** — [customer@1638.cloud](mailto:customer@1638.cloud)
- **Bug reports** — [issues](https://github.com/nekzabirov/IGaming-Game-Engine/issues/new/choose)
