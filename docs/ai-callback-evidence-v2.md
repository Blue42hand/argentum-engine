# Recorded callback evidence v2

`RecordedAiCallbackController` is an optional extension beside the existing ordinary
and v1 recording interfaces. It returns the normal native `ActionResponse` plus
the original offered index and exact submitted parameter object for action choices.
Structured choices keep their native typed response. The server validates evidence
under its session lock and runs the existing execution authority; recording failures
never supply a strategy or change rules admission.

The server authors masked inputs for action, structured, mulligan, bottom-card and
payment-correction callbacks. It records `ai_callback_input`, an accepted
`ai_callback_result` emitted directly after `native_transition`, or an explicit
`ai_callback_disposition`. Each uses one native correlation ID. Result observations
come from the actual resulting seat projection. Native administrative state remains
outside the controller interface and model input. The v2 schema hash is SHA256 of
`argentum-ai-callback-seat-evidence-v2`; context state hashes cover exact UTF-8 JSON.

Actions are authenticated by reconstructing the selected native offer with
`ActionParameterizer` and matching both original and actual submitted concrete
actions. The result keeps the raw selected parameters alongside the native action.
Structured responses must match the native seat and current question. Mulligan and
bottom offers use their own seat's current masked hand/count/phase, permitting
independent other-seat mulligans. Accepted receipts precede terminal sealing;
superseded/cancelled/rejected/stale/overridden contexts cannot certify a choice.

Initialization declares `callbackEvidenceVersion=2` and the initial native state
digest. On persistence restoration, `callback_resume_state` reports the restored
native state digest. The consumer must match that against its committed source
position and retain all callback gaps. Source/hash continuity is evidence of the
recording protocol, not proof of exact replay or host qualification.

Ordinary browser protocol, game rules, controller ownership and v1 evidence remain
unchanged. This internal recording capability adds no frontend decision flow.
