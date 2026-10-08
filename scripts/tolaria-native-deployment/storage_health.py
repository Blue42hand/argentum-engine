"""Metadata-only storage/backup gates. No deletion or reading game payloads."""
import math


def storage_decision(status, *, now, min_free_bytes, max_backup_age_seconds):
    for value in (now, min_free_bytes, max_backup_age_seconds):
        if type(value) not in (int, float) or not math.isfinite(value) or value < 0:
            raise ValueError("invalid storage policy")
    for key in ("freeBytes", "durableBytes", "pendingRecordWrites"):
        if type(status.get(key)) is not int or status[key] < 0:
            return "hold_missing_storage_evidence"
    if status.get("canonicalRoot") != "/var/lib/commander-gym":
        return "hold_wrong_recording_root"
    if status.get("backupCoversRecorderManifest") is not True:
        return "hold_backup_coverage"
    timestamp = status.get("lastVerifiedBackupUnix")
    if (type(timestamp) not in (int, float) or not math.isfinite(timestamp)
        or not 0 <= now - timestamp <= max_backup_age_seconds):
        return "hold_backup_stale"
    if status.get("recordingHealthy") is not True:
        return "hold_recording_failure"
    if status["freeBytes"] < min_free_bytes:
        return "pause_new_admission_low_storage"
    return "storage_ready"
