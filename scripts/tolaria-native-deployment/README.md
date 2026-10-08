# Native always-on deployment

Opt-in native lifecycle controls and a constrained Linux host updater are
implemented here. These files do not install accounts, services, firewall rules,
Tailscale settings or credentials. Native recording producers and their protected
executable closure must be integrated and qualified before admission can open.

The server executes a prebuilt JAR with built-in engine AI and an explicit keyless
environment. Paid AI remains a separately approved per-session sidecar with its
existing budget gate. The compiled static frontend and JAR are one root-owned
immutable release pair. No builds, Git commands or Vite daemon run at boot.

## Native lifecycle protocol 1

Set `NATIVE_LIFECYCLE_ENABLED=true`, exact `APP_VERSION`, `NATIVE_RELEASE_ID` and
`NATIVE_GYM_SHA` through the protected launcher. Opt-in requires in-memory native
repositories; existing deployments retain the disabled default. Admission starts
closed on every boot. The private Unix socket is
`/run/argentum-play/lifecycle.sock`, mode 0600 in a native-owned 0700 directory.
The JDK validates peer credentials. Unsupported credentials or pre-existing paths
refuse startup, without a weaker fallback or replacing an existing socket.

Each connection sends one JSON object plus newline, at most 4095 bytes and within
two seconds. Replies contain metadata only. There is no HTTP control endpoint or
new password/token. Root is the default updater peer; the native execution user
alone submits recorder status. Every request contains `protocol: 1` and `op`:

| Operation | Additional fields | Peer |
|---|---|---|
| `status` | none | updater or native recorder user |
| `drain` | `bootId` | updater |
| `resume` | `bootId`, `releaseId` | updater |
| `recording` | `bootId`, `releaseId`, `gymSha`, `recoveryComplete`, `recordingHealthy`, `producerCoverageComplete`, `pendingRecordWrites`, `recordingSchemaVersion`, `durableBytes`, `drainId`, `drainComplete` | native recorder user |

Unknown fields, mismatched epochs/pins and invalid counters are rejected without
logging payloads. Recorder health expires after ten monotonic seconds.
Expired/unhealthy recording latches admission closed until verified resume.
`producerCoverageComplete` means all required native capture producers are
connected; it does not certify dataset conversion or authorize training.

Drain serializes against new-game registration. Start commands reserve admission
before altering player/lobby state; reservations remain counted until handling
finishes. Existing saves, moves and reconnects continue. Status counts nonterminal
sessions, idle/pregame sessions, reserved starts and pending quick/sealed/tournament
activities. No AI-callback or public lobby-idle inference is used. Players can
leave/stop idle lobbies explicitly; updates impose no game timeout or forced loss.

Every drain has a fresh `drainId`. The recorder acknowledges that exact drain only
after required finalization and queued writes are durable. Native
`recordingDrainComplete` requires that acknowledgement and zero sessions,
reservations, pending activities and writes. A pre-drain zero counter cannot
authorize switching.

## Trusted releases and host adapter

`/srv/argentum-play/releases/<releaseId>/` contains only `game-server.jar`, `web/`
and `release.json`; `current` is one root-owned symlink to the entire pair.
Artifacts and parents must be root-owned, not group/world writable and free of
symlinks. Installation and the recorder's complete protected execution closure
remain stage-owner work; the updater never fetches/builds or executes downloaded
commands.

The root-owned `/etc/argentum-play/promotions/<releaseId>.json` must exactly match
the installed manifest. Fields are `releaseId`, unique monotonic
`promotionSequence`, `channel: tolaria-stable`, `engineSha`, `gymSha`,
`frontendEngineSha`, `jarSha256`, `frontendTreeSha256`, `qualificationSha256`,
`recordingSchemaVersion`, `paidProvidersEnabled: false`, `terminalFixSha` and every
boolean in `release_gate.GATES`. Unknown fields are rejected. `releaseId` hashes
compact sorted JSON of every field except itself. Frontend hashing uses sorted
relative UTF-8 paths plus NUL plus binary SHA-256 file hashes.

Root custody is the promotion trust boundary: builder/runtime identities cannot
write promotions, qualifications, policy, installed releases or active links.
Hashes and self-asserted green booleans from downloads are insufficient. The
separately reviewed promotion reader/installer must verify provenance, CI, exact
bytes and full qualification before publishing this protected envelope. This code
does not supply or silently provision its credential.

`qualifications/<releaseId>.json` is protected private metadata, checksum-bound by
`qualificationSha256`. It joins exact engine/Gym/JAR/frontend identities, every
gate and `cleanupVerified: true`. Qualification includes actual isolated keyless
native HTTP/WebSocket play, masking, idle drain, terminal flush, restart/crash
integrity and private restore/replay. Offline safety tests cannot produce it.
Gym terminal fix `598a60a4120ff2d7ba69fd90b16eccc85e5e30fa` must enter the tuple.

`host_adapter.py update` serializes operations, validates evidence, requests drain
and defers while any game/activity/write remains or evidence is missing. It makes
a final fresh check, privately fsyncs intent, stops facade/backend, atomically
switches the pair and starts closed. A healthy matching boot with recorder recovery
permits admission. Failure before opening restores the previous pair, still closed.
Uncertain admission or failed rollback holds for operator review. An unfinished or
rolled-back transaction prevents automatic retry/reopening. No forced game drain.

`admit-current` supports ordinary boot/restart readiness. It verifies protected
promotion, qualification, storage and recording evidence before opening the current
release. It cannot override an unfinished update; both operations share a lock and
journal. It never switches versions or starts games. Private operational metadata
lives under `/var/lib/argentum-updater`, which is not a learning archive.

## Canonical recording, storage and backup

Recorder owner supplies producers, per-game routing, writer permissions,
independent expected source counts, completion/integrity receipts, byte counters,
recovery and backup discovery. Proposed canonical placement is
`/var/lib/commander-gym/runs/<run_id>/manifest.json`, schema 1, kind
`commander-gym.game-capture-manifest`. `ready_for_analysis` and `recording_complete`
are distinct. Discovery/integrity use `discover_finalized_manifests` and
`verify_finalized_manifest` in `commander_gym.game_journal`; analysis stays under
the same run's `analysis/`. No alternate archive, raw WebSocket capture or public
training API is added.

`updater-policy.json` requires approved `channel`, `minFreeBytes` and
`maxBackupAgeSeconds`. `backup-health.json` supplies independently verified
`lastVerifiedBackupUnix` and `backupCoversRecorderManifest`. Free bytes are sampled
on the canonical data mount; durable bytes/write health come from the recorder.
Missing/old backups hold updates and admission. Low capacity closes new admission
and appends a private metadata alert. Reserve enough capacity for active games to
finish. No versions, records, journals or evidence are pruned.

## Facade and installation gates

The nginx example remains loopback-only until the approved network profile is
applied. It permits native catalog/config/deck validation, public quick-game listings
and `/game`; other APIs return 404, including public/private full-state replay,
admin stats, debug and paid-assistant routes. Explicit WebSocket origins are
enforced at the facade because the original engine allows wildcard origins.
Access logs omit URI/query/body/address/identity; decoder/handler errors omit raw
exception payloads. Raw training data never belongs in the static document root.

Before installation, qualify the exact matched tuple and validate nginx with
`nginx -t`, units with `systemd-analyze verify`, boot/restart, drain/reconnect,
rollback/recovery, complete producers, private restore and token/no-API logging
fixtures. Verify targeted canonical writer/backup-reader access and that native/build
identities cannot read provider keys, paid ledger, private imports or unrelated runs.
Confirm current LAN address, firewall and tailnet policy; preserve unrelated rules
and services. Never enable public Funnel or router forwarding.

Run `python3 -m unittest discover -s scripts/tolaria-native-deployment -v` for
offline tests and `just test-server` for server gates. Linux peer-client tests run
in CI; daemon/config/integration qualification is separate.

Existing Gym/gateway deployments, CI migration and retiring other hosts are outside
this workstream. API references: [JDK 21 Unix channels](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/ServerSocketChannel.html)
and [peer credentials](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.net/jdk/net/ExtendedSocketOptions.html#SO_PEERCRED).
