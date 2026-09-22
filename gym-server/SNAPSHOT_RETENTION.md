# Gym snapshot retention

Gym snapshots retain immutable in-process `GameState` references until clients explicitly dispose their handles. Explicit `DELETE /snapshots` and `DELETE /snapshots/batch` remain the normal cleanup path.

Persistent Gym hosts may additionally enable an idle snapshot TTL so state abandoned by a crashed or disconnected trainer can be reclaimed:

```bash
GYM_SERVER_SNAPSHOT_TTL_MS=600000
GYM_SERVER_SNAPSHOT_REAPER_INTERVAL_MS=30000
```

`GYM_SERVER_SNAPSHOT_TTL_MS=0` is the default and disables automatic cleanup. TTL values must be non-negative.

A successful snapshot load or restore renews the handle's activity time. Snapshot lifetime is independent from the source environment: disposing an environment does not dispose snapshots made from it.

Snapshot-producing HTTP requests protect newly-created handles until the response has published them to the caller. Successful publication renews those handles to a fresh full TTL before releasing the protection, while unrelated older snapshots remain eligible for cleanup. This prevents a short TTL or delayed response from reclaiming a handle before the client can receive it.

The retention policy is transport/lifecycle behavior only. `SnapshotCodec` owns the race-safe per-handle activity and cleanup semantics; `gym-server` only schedules the optional reaper and marks the HTTP response-publication boundary.
