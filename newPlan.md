# FocusKeeper Reboot Plan (Phase 1.x ~ 15.x)

## Goal
- Reset codebase and rebuild from Phase 1 with a clearer product definition.
- Keep release target at completion of Phase 15.

## Legacy Baseline
- Old implementation up to 12.x is archived in branch: `archive/pre-reboot-2026-02-28`.

## Phase Roadmap
- 1.x Foundation: project bootstrap, build, env profiles
- 2.x Core conventions: error model, API response standard, logging
- 3.x Domain core: user/challenge/wallet entities and invariants
- 4.x Removed: old AI task decomposition phase (not reused)
- 5.x Wallet rules: deposit, penalty, reward consistency
- 6.x Challenge integration: settlement flow with wallet
- 6A~6C Verifier architecture: strategy + GitHub verifier
- 7.x Auth: OAuth2 user identity and session/token policy
- 8.x Shop: item purchase and inventory linkage
- 9.x API standardization and endpoint hardening
- 9.5.x Infra baseline: Docker + monitoring + load baseline
- 10.x Redis optimization: cache and ranking
- 11.x Legacy Kafka events: to be replaced by outbox path
- 12.x Social feed: follow + feed fan-out
- 12.5 Safety net: test/CI hardening
- 12.9 Refactor baseline: domain purity + outbox transition
- 13.x Burnout analytics: Spring Batch + RDB (Track A)
- 13.x Optional scale-up: Spark offline track (Track B)
- 14.x Watchtower: Sentry + Prometheus/Grafana + alert rules
- 15.x Executive Assistant: async AI retrospective/coaching

## Non-Negotiable Architecture Rules
- Business logic must not directly call external messaging brokers.
- Domain events are published via `ApplicationEventPublisher`.
- Outbox persistence must be in the same transaction as business state changes.
- Relay worker must enforce idempotency with `event_id`.

## Release Scope
- Launch target: complete through Phase 15.x.
- Spark scale-up and MCP are post-release options.
