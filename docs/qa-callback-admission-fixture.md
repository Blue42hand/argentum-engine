# QA callback admission fixture proposal

Only a new reviewed matched native/Gym qualification build may enable
`native.qa.callback-gate-enabled=true`. Default false preserves production
callback dispatch and the existing lifecycle status shape. This is an explicit
QA fixture source proposal; it issues no profile, authority, executable inventory
extension or permission to activate a runtime.

Both asynchronous AI message processing and payment corrections enter the QA gate
before native callback evidence issuance. Active-handler counters cover controller
execution, native application callback and final disposition cleanup. A private
lifecycle socket status in this mode reports enabled/paused/active/waiting/failure
counters and pause generation. The gate bounds combined waiting/active handlers
at64 and paused admission wait at900 seconds. Overflow/timeout keeps failure visible;
cancellation releases queued admission without calling the controller. Native
legality, recording receipt/disposition and technical retry limits are unchanged.

The existing private0600 Unix socket's updater peer may send exact protocol1
operations `qa-callback-pause` with fresh bootId, or `qa-callback-resume` with the
same bootId and numeric generation returned by pause/status. Resume requires
paused admission, active0, admissionFailures0 and exact generation. Disabled QA,
wrong peer, wrong boot, extra fields or stale generation fail. No HTTP/WebSocket
control route is added. Do not use these counters as `recordingDrainComplete` or
as a canonical recording receipt.

The reviewed owner phase controller must pause its human submissions, pause native
callback admission, wait for observed active0, and verify actual durable Gym/native
callback joins through Gym's read-only `qa_callback_drain` source helper. It then
stops/reopens only recorder, retaining native alive/paused, verifies old prefix and
identical run/pins/declared runtime context plus recorder readiness/custody, resumes
the exact generation and only then resumes human submissions. All unsupported,
failed, stale, cancelled or missing joins remain failure. Native game terminal and
complete finalized v2 envelope must come from ordinary authoritative application,
never a synthetic receipt. CI handler tests do not prove guest transport readiness.

Socket reconnect is in-memory session continuity. Recorder reopen is journal
recovery. Native process/session recovery is a distinct test: current supported
RedisGameRepository/SessionRecoveryService needs cache.redis.enabled=true, while
selected Q/production contracts require Redisfalse and NativeControlSocket requires
InMemoryGameRepository. No config-only supported Redis Q exists. A separate
reviewed test-only persistence variant and exact dependency/resource/security
proposal is required; this change does not enable Redis or relax those guards.
