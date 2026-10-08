"""Offline rollout gate; performs no installation, restart, fetch, or promotion."""
import re

SHA = re.compile(r"[a-f0-9]{40}\Z")
DIGEST = re.compile(r"[a-f0-9]{64}\Z")
GATES = ("requiredCiPassed", "nativeNoApiSmokePassed", "recordingRestorePassed",
         "seatMaskingPassed", "terminalLifecyclePassed", "terminalFixIncluded")


def rollout_decision(candidate, current, lifecycle, *, now, promotion_verified=False):
    """Caller verifies trusted promotion/provenance and file hashes separately.

    Explicit drain acknowledgement and fresh authoritative counts prevent racing
    a new game's admission against an apparently idle server. Missing evidence
    always holds. This is a planner, not an executable updater.
    """
    if not promotion_verified:
        return "hold_unverified_promotion"
    if (candidate.get("channel") != "tolaria-stable"
        or type(candidate.get("engineSha")) is not str
        or not SHA.fullmatch(candidate["engineSha"])):
        return "hold_release_identity"
    if type(current.get("engineSha")) is not str or not SHA.fullmatch(current["engineSha"]):
        return "hold_current_identity"
    if type(candidate.get("gymSha")) is not str or not SHA.fullmatch(candidate["gymSha"]):
        return "hold_compatibility_identity"
    if candidate.get("frontendEngineSha") != candidate["engineSha"]:
        return "hold_mismatched_frontend"
    if any(type(d.get(k)) is not str or not DIGEST.fullmatch(d[k])
           for d in (candidate, current)
           for k in ("releaseId", "jarSha256", "frontendTreeSha256")):
        return "hold_artifact_identity"
    if candidate.get("paidProvidersEnabled") is not False:
        return "hold_paid_provider_config"
    if any(candidate.get(k) is not True for k in GATES):
        return "hold_acceptance_evidence"
    if any(type(d.get("recordingSchemaVersion")) is not int or d["recordingSchemaVersion"] < 1
           for d in (candidate, current)):
        return "hold_recording_schema_missing"
    if candidate["recordingSchemaVersion"] != current["recordingSchemaVersion"]:
        return "hold_recording_migration_requires_review"
    if (lifecycle.get("engineSha") != current.get("engineSha")
        or lifecycle.get("releaseId") != current.get("releaseId")):
        return "hold_status_release_mismatch"
    if lifecycle.get("gymSha") != current.get("gymSha"):
        return "hold_status_recorder_identity_mismatch"
    if (type(lifecycle.get("recordingSchemaVersion")) is not int
        or lifecycle.get("recordingSchemaVersion") != current.get("recordingSchemaVersion")):
        return "hold_status_recording_schema_mismatch"
    if (type(lifecycle.get("bootId")) is not str or not lifecycle["bootId"]
        or lifecycle.get("recoveryComplete") is not True):
        return "hold_recovery_incomplete"
    observed = lifecycle.get("observedUnix")
    if type(observed) not in (int, float) or not 0 <= now - observed <= 10:
        return "hold_stale_status"
    if lifecycle.get("acceptingNewGames") is not False:
        return "request_drain"
    if lifecycle.get("drainAcknowledged") is not True:
        return "hold_unacknowledged_drain"
    for name in ("activeGames", "pendingActivities", "pendingRecordWrites"):
        count = lifecycle.get(name)
        if type(count) is not int or count < 0:
            return "hold_invalid_counts"
        if count:
            return "defer_active_games_or_unflushed_records"
    if lifecycle.get("recordingHealthy") is not True:
        return "hold_recording_failure"
    if lifecycle.get("recordingDrainComplete") is not True:
        return "hold_recording_drain_barrier"
    if candidate.get("releaseId") == current.get("releaseId") and all(
        candidate.get(k) == current.get(k)
        for k in ("engineSha", "gymSha", "jarSha256", "frontendTreeSha256")
    ):
        return "already_current"
    return "eligible_after_verified_drain"
