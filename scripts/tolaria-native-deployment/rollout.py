"""Host-independent rollout transaction; no SSH, shell or credential access.

The host adapter is deliberately not supplied until recorder lifecycle and owner
permissions are agreed. Methods must raise on missing evidence. A trusted
promotion verifier must authenticate the entire manifest, not just its hashes.
"""
import hashlib
from pathlib import Path
from release_gate import rollout_decision


def artifact_digests(release):
    """Hash a closed regular-file tree; reject links and extra root artifacts."""
    root = Path(release)
    if root.is_symlink() or not root.is_dir():
        raise ValueError("invalid release directory")
    if {p.name for p in root.iterdir()} not in ({"game-server.jar", "web"}, {"game-server.jar", "web", "release.json"}):
        raise ValueError("unexpected release contents")
    metadata = root / "release.json"
    if metadata.exists() and (metadata.is_symlink() or not metadata.is_file() or metadata.stat().st_size > 65536):
        raise ValueError("invalid release metadata")
    jar = root / "game-server.jar"
    web = root / "web"
    if jar.is_symlink() or not jar.is_file() or web.is_symlink() or not web.is_dir():
        raise ValueError("invalid artifact layout")
    digest = hashlib.sha256()
    count = 0
    for p in sorted(web.rglob("*")):
        if p.is_symlink() or not (p.is_file() or p.is_dir()):
            raise ValueError("nonregular frontend artifact")
        if p.is_file():
            digest.update(p.relative_to(web).as_posix().encode() + b"\0")
            digest.update(hashlib.sha256(p.read_bytes()).digest())
            count += 1
    if not count or not (web / "index.html").is_file():
        raise ValueError("missing frontend")
    return hashlib.sha256(jar.read_bytes()).hexdigest(), digest.hexdigest()


class Rollout:
    """One attempt under host.exclusive_lock(), never forces a game's timeout.

    host.record must durably fsync a private transaction journal on Tolaria.
    host.stage validates root ownership, immutable permissions, free bytes,
    provenance and backup health; smoke uses isolated ports with admission shut.
    All control calls use a permission-checked private socket, never public HTTP.
    host.switch atomically switches one link containing BOTH server and frontend.
    host.restart_closed must start with admission CLOSED, including after reboot.
    host.health returns fresh recorder recovery/flush/identity and native health.
    host.resume is the only admission-opening operation. Uncertain resume results
    require operator review, since new games may already have been admitted.
    """

    def __init__(self, host, clock):
        self.host, self.clock = host, clock

    def attempt(self, candidate):
        h = self.host
        with h.exclusive_lock():
            h.assert_no_unfinished_transaction()
            h.verify_promotion(candidate)
            current = h.current_manifest()
            h.stage_and_verify(candidate)
            h.smoke_closed(candidate)
            status = h.status()
            decision = rollout_decision(candidate, current, status,
                                        now=self.clock(), promotion_verified=True)
            if decision == "request_drain":
                h.drain(status["bootId"])
                status = h.status()
                decision = rollout_decision(candidate, current, status,
                                            now=self.clock(), promotion_verified=True)
            if decision != "eligible_after_verified_drain":
                return decision
            # Preparation can take time: a second authoritative check is mandatory.
            final = h.status()
            if final.get("bootId") != status.get("bootId"):
                return "hold_boot_changed"
            decision = rollout_decision(candidate, current, final,
                                        now=self.clock(), promotion_verified=True)
            if decision != "eligible_after_verified_drain":
                return decision
            h.record("activation_intent", current, candidate)
            try:
                h.switch(candidate)
                h.restart_closed()
                if not h.health(candidate):
                    raise RuntimeError("candidate health failed")
            except Exception:
                # No admission opening attempted: safe to restore previous pair.
                try:
                    h.switch(current)
                    h.restart_closed()
                    if not h.health(current):
                        raise RuntimeError("rollback health failed")
                    h.record("rolled_back_closed", current, candidate)
                except Exception:
                    h.record("operator_review_required", current, candidate)
                    raise
                return "rolled_back_closed"
            h.record("healthy_closed", current, candidate)
            h.record("admission_intent", current, candidate)
            try:
                h.resume(candidate)
            except Exception:
                h.record("admission_uncertain_operator_review", current, candidate)
                # Never roll back after attempting to admit new games.
                raise
            h.record("completed", current, candidate)
            return "activated"
