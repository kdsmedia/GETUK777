# Configuration

## Environment variables

| Variable | Description | Default |
| --- | --- | --- |
| `HTTP_PORT` | HTTP server port | `8080` |
| `GRPC_PORT` | gRPC server port | `5050` |
| `DATABASE_URL` | JDBC PostgreSQL URL | `jdbc:postgresql://localhost:5432/game_db` |
| `DATABASE_USER` | Database username | — |
| `DATABASE_PASSWORD` | Database password | — |
| `WALLET_GRPC_HOST` | Wallet service gRPC host | `localhost` |
| `WALLET_GRPC_PORT` | Wallet service gRPC port | `5555` |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `S3_ENDPOINT` | S3-compatible storage endpoint | — |
| `S3_REGION` | S3 region | — |
| `S3_ACCESS_KEY` | S3 access key | — |
| `S3_SECRET_KEY` | S3 secret key | — |
| `S3_BUCKET` | S3 bucket name | — |
| `RABBITMQ_URL` | RabbitMQ AMQP URL | `amqp://guest:guest@localhost:5672` |
| `RABBITMQ_EXCHANGE` | RabbitMQ exchange name | `casino-engine` |

Copy `.env.example` to `.env` for local development; production
deployments should inject these through your orchestrator's secret
management (Kubernetes secrets, Vault, AWS Secrets Manager, etc.).

## Infrastructure

`docker-compose.yml` provisions the local development stack:

| Service | Port(s) | Purpose |
| --- | --- | --- |
| PostgreSQL 16 | 5432 | Database |
| RabbitMQ 3 | 5672, 15672 | Message broker + management UI |
| Redis 7 | 6379 | Player limits cache |
| MinIO | 9000, 9001 | S3-compatible file storage + console |

For production:

- **PostgreSQL** — managed (RDS, Cloud SQL, Aiven) recommended; HA with
  read replicas if you need analytics queries on the same dataset
- **RabbitMQ** — clustered for HA; consider CloudAMQP or Amazon MQ
- **Redis** — single primary is fine for player limits (TTL-bounded);
  upgrade to cluster only if you scale past one node
- **S3** — actual AWS S3, GCS, or any S3-compatible service. MinIO is
  for local only

## Build

```bash
./gradlew build          # Build (also runs installDist)
./gradlew test           # Run all tests
./gradlew run            # Run application (HTTP :8080, gRPC :5050)
./gradlew runSync        # Run aggregator sync CLI locally
./gradlew generateProto  # Generate gRPC stubs from proto files
./gradlew grpcClientJar  # Build gRPC client JAR for consumers
```

## Docker

```bash
./gradlew build          # Creates build/distributions/casino-engine-*.tar
docker-compose up -d     # Starts infra + app + sync job
```

## Two entrypoints

| Entrypoint | Command | Purpose |
| --- | --- | --- |
| Main server | `/app/bin/casino-engine` | HTTP + gRPC + RabbitMQ consumers |
| Sync job | `/app/bin/sync-aggregators` | One-shot game sync from all active aggregators |

The sync job is typically run as a cron / Kubernetes CronJob on a
schedule (every few hours) to keep the game catalogue up to date with
aggregators.
