# Security Policy

Casino Engine handles money flow in iGaming platforms. Security
vulnerabilities are taken seriously and responded to promptly.

## Reporting a vulnerability

**Do not open a public GitHub issue for a security vulnerability.**

Email security disclosures to: **[customer@1638.cloud](mailto:customer@1638.cloud)**

Include in your report:

- A clear description of the vulnerability
- Steps to reproduce, or a proof-of-concept
- The version or commit hash where you observed it
- The potential impact (information disclosure, financial loss, DoS,
  privilege escalation, etc.)
- Your name and contact details (for credit, if you wish)

We will acknowledge receipt within **48 hours** and provide an initial
assessment within **7 days**. For critical issues affecting funds or
player data, we aim to ship a fix within **14 days** of confirmed
reproduction.

## Scope

In scope:

- The `nekzabirov/IGaming-Game-Engine` repository
- Released versions and the `master` branch
- gRPC API (`game.v1`) and aggregator webhook endpoints
- Authentication, authorisation, and signature verification logic
- Wallet adapter contract and idempotency handling
- Aggregator integration adapters (Pragmatic Play, OneGameHub, Pateplay)

Out of scope (please don't report these):

- Vulnerabilities in dependencies — report those upstream
- Issues that require physical access to a host running the engine
- Social-engineering attacks against contributors or staff
- Self-hosted deployments where the operator misconfigured infrastructure
  (open ports, default passwords, exposed admin endpoints)
- Vulnerabilities in third-party aggregator platforms

## Production deployment considerations

Casino Engine is designed to run **behind a public-facing decorator** that
handles authentication, authorisation, rate limiting, and request
validation. Exposing the engine's gRPC port directly to the internet is
a configuration error, not a vulnerability in the engine.

If you are evaluating Casino Engine for production use, please review:

- [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) — deployment topology
  recommendations
- [`docs/ADAPTERS.md`](./docs/ADAPTERS.md) — required adapter contracts
  including idempotency
- [`docs/CONFIGURATION.md`](./docs/CONFIGURATION.md) — environment
  variables and secrets handling

## Disclosure policy

We follow **coordinated disclosure**:

1. You report the issue privately
2. We confirm, develop a fix, and prepare a security advisory
3. We notify known production deployments (operators using Casino Engine
   directly or via [1638.cloud](https://1638.cloud))
4. We release the fix and publish the advisory simultaneously
5. We credit you in the advisory (unless you prefer to stay anonymous)

We do not currently run a bug bounty programme. For severe issues
affecting production deployments on the 1638.cloud platform, we may
offer a discretionary reward — this is handled case by case.

## PGP

For high-sensitivity disclosures, you may request a PGP key by emailing
[customer@1638.cloud](mailto:customer@1638.cloud) with the subject line
`PGP key request — security`.

## Thank you

Security researchers who report responsibly help every operator running
this code. If you find something, please reach out — we're easy to work
with and will give you credit.
