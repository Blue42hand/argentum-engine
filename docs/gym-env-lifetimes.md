# Gym server environment lifetimes

The Gym server keeps environments in memory until they are explicitly disposed. Persistent servers
can optionally enable an idle lease so abandoned environments from crashed or disconnected trainers
do not accumulate forever.

## Configuration

Leases are **disabled by default**, preserving the existing explicit-disposal contract.

```bash
GYM_SERVER_ENV_TTL_MS=600000              # reap after 10 minutes without leased activity
GYM_SERVER_ENV_REAPER_INTERVAL_MS=30000   # scan every 30 seconds (default)
```

`GYM_SERVER_ENV_TTL_MS=0` disables the reaper. The TTL must be non-negative.

## Activity and heartbeat semantics

Singular requests whose path contains an environment ID (`/envs/{id}`, `/envs/{id}/step`,
`/envs/{id}/decision`, `/envs/{id}/reset`, `/envs/{id}/fork`, `/envs/{id}/snapshot`, and
`/envs/{id}/restore`) automatically hold and renew that environment's lease for the request.

Batch endpoints do not encode their environment IDs in the URL. When leases are enabled, batch
clients should send the participating IDs in a comma-separated header:

```text
X-Argentum-Gym-Env-Ids: <env-a>,<env-b>,<env-c>
```

The same header can be sent periodically as a lightweight heartbeat on any Gym HTTP request when a
trainer may spend longer than the TTL in inference, search, checkpointing, or other work between
engine calls. It is also accepted on singular requests and de-duplicated with the path-derived ID.

A newly discovered live environment receives one full TTL grace period before it can be reaped. A
leased request increments an in-flight count before controller execution; the scheduled reaper skips
any environment with a leased request in flight and refreshes its last-activity timestamp when the
request finishes. Explicit `DELETE /envs` remains the preferred normal cleanup path and remains
idempotent.

## Boundary

This is an HTTP-server lifecycle facility, not game policy. `MultiEnvService` remains the
transport-agnostic authoritative environment service; the lease manager only calls its existing
`listEnvs()` and `dispose(...)` methods. It does not inspect game state, cards, player strategy, or
model behavior.

The raw Gym server still has no application-layer authentication. Keep it on loopback or behind an
authenticated gateway/reverse proxy.
