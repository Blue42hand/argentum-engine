# Gym server environment and snapshot lifetimes

The Gym server keeps environments and snapshots in memory until clients explicitly dispose them.
Persistent servers can optionally enable independent idle TTLs so abandoned state from crashed or
disconnected trainers does not accumulate forever.

## Configuration

Automatic cleanup is **disabled by default**, preserving the existing explicit-disposal contract.

```bash
GYM_SERVER_ENV_TTL_MS=600000                   # reap envs after 10 minutes without leased activity
GYM_SERVER_ENV_REAPER_INTERVAL_MS=30000        # scan envs every 30 seconds (default)
GYM_SERVER_SNAPSHOT_TTL_MS=600000              # reap snapshots after 10 minutes without save/load activity
GYM_SERVER_SNAPSHOT_REAPER_INTERVAL_MS=30000   # scan snapshots every 30 seconds (default)
```

A TTL of `0` disables that reaper. TTL values must be non-negative. Environment and snapshot TTLs
are intentionally independent and may be enabled separately.

## Environment activity and heartbeat semantics

Singular requests whose path contains an environment ID (`/envs/{id}`, `/envs/{id}/step`,
`/envs/{id}/decision`, `/envs/{id}/reset`, `/envs/{id}/fork`, `/envs/{id}/snapshot`, and
`/envs/{id}/restore`) automatically hold and renew that environment's lease for the request.

Existing-environment batch endpoints derive their participating IDs from the parsed request body and
automatically hold and renew those leases while the authoritative batch operation runs. Callers do
not need to repeat those IDs in a header for ordinary observe/reset/step/decision/fork/snapshot/restore
batch requests.

`X-Argentum-Gym-Env-Ids` remains available as an optional comma-separated heartbeat or early-request
lease hint:

```text
X-Argentum-Gym-Env-Ids: <env-a>,<env-b>,<env-c>
```

It is useful when a trainer may spend longer than the environment TTL in inference, search,
checkpointing, or other work between engine calls. It can also protect named environments before a
batch request reaches controller dispatch. Header-derived and body-derived leases compose safely, so
supplying the header for a normal batch request is redundant but valid. The header is also accepted on
singular requests and de-duplicated with the path-derived ID.

A newly discovered live environment receives one full TTL grace period before it can be reaped. A
leased request increments an in-flight count before controller execution; the scheduled reaper skips
any environment with a leased request in flight and refreshes its last-activity timestamp when the
request finishes. Explicit `DELETE /envs` remains the preferred normal cleanup path and remains
idempotent.

Resource-producing `POST /envs`, `/envs/deckbuild`, `/envs/create-batch`, `/envs/{id}/fork`, and
`/envs/fork-batch` requests also hold a response-publication scope. A caller cannot lease a newly
created environment before its ID has been returned, so the reaper may reconcile bookkeeping during
that bounded scope but does not dispose environments. After a successful response completes, any new
environments receive a fresh full TTL before ordinary idle cleanup resumes.

## Snapshot activity and independence

A snapshot retains an immutable `GameState` reference in `SnapshotCodec` until the handle is disposed.
When snapshot TTL cleanup is enabled, saving a snapshot starts its idle period and each successful
snapshot load/restore renews it. `DELETE /snapshots` and `DELETE /snapshots/batch` remain the preferred
normal cleanup paths and remain idempotent.

Snapshot lifetime is deliberately **not tied to source-environment lifetime**. A trainer may dispose
the source environment and keep a snapshot for a later restore or branch. Environment reaping therefore
does not delete snapshots; the independent snapshot TTL exists specifically to reclaim handles that a
client abandons without explicit cleanup.

`SnapshotCodec` serializes load/expiry decisions per handle, so a load that renews a snapshot before
its cleanup check cannot be removed using a stale last-access value. Once a snapshot has actually
expired and been reclaimed, later restore attempts fail as they do after explicit disposal.

## Boundary

These are lifecycle facilities, not game policy. `MultiEnvService` remains the transport-agnostic
authoritative environment service. Environment cleanup delegates to its existing `listEnvs()` /
`dispose(...)` boundary, while snapshot retention is owned by the existing `SnapshotCodec`. Neither
facility inspects game strategy, cards, player policy, or model behavior.

The raw Gym server still has no application-layer authentication. Keep it on loopback or behind an
authenticated gateway/reverse proxy. Spring Actuator/Micrometer request metrics are independent of
lifecycle cleanup and remain available for persistent-host qualification.
