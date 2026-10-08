"""Constrained Linux updater for root-installed, root-approved release pairs.

No network fetch, builds, credential lookup, shell interpolation or deletion.
Promotion creation and artifact installation belong to the separately approved
release reader/installer, not to the runtime/build identities or this updater.
"""
import contextlib
import fcntl
import hashlib
import json
import os
from pathlib import Path
import pwd
import socket
import stat
import struct
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parent))
from release_gate import DIGEST, GATES, SHA
from rollout import Rollout, artifact_digests
from storage_health import storage_decision

ROOT = Path("/srv/argentum-play")
CONFIG = Path("/etc/argentum-play")
STATE = Path("/var/lib/argentum-updater")
CONTROL = Path("/run/argentum-play/lifecycle.sock")
TERMINAL_FIX = "598a60a4120ff2d7ba69fd90b16eccc85e5e30fa"


def protected(path, *, directory=False):
    path = Path(path)
    info = path.lstat()
    expected = stat.S_ISDIR if directory else stat.S_ISREG
    if info.st_uid != 0 or info.st_mode & 0o022 or not expected(info.st_mode):
        raise ValueError("untrusted installed path")
    for parent in path.parents:
        info = parent.lstat()
        if not stat.S_ISDIR(info.st_mode) or info.st_uid != 0 or info.st_mode & 0o022:
            raise ValueError("untrusted installed parent")


def read_json(path):
    protected(path)
    with open(path, "rb") as stream:
        raw = stream.read(65537)
    if len(raw) > 65536:
        raise ValueError("oversized trusted metadata")
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("duplicate trusted metadata key")
            result[key] = value
        return result
    result = json.loads(raw, object_pairs_hook=unique)
    if type(result) is not dict:
        raise ValueError("invalid trusted metadata")
    return result


def release_identity(manifest):
    payload = {k: v for k, v in manifest.items() if k != "releaseId"}
    return hashlib.sha256(json.dumps(payload, sort_keys=True, separators=(",", ":"),
                                     allow_nan=False).encode()).hexdigest()


def validate_manifest(manifest):
    required = {"releaseId", "promotionSequence", "channel", "engineSha", "gymSha",
                "frontendEngineSha", "jarSha256", "frontendTreeSha256", "qualificationSha256",
                "recordingSchemaVersion", "paidProvidersEnabled", "terminalFixSha", *GATES}
    if set(manifest) != required or manifest["channel"] != "tolaria-stable":
        raise ValueError("invalid release manifest")
    if any(type(manifest[k]) is not str or not SHA.fullmatch(manifest[k])
           for k in ("engineSha", "gymSha", "frontendEngineSha")):
        raise ValueError("invalid source identity")
    if manifest["frontendEngineSha"] != manifest["engineSha"]:
        raise ValueError("unmatched frontend")
    if any(type(manifest[k]) is not str or not DIGEST.fullmatch(manifest[k])
           for k in ("releaseId", "jarSha256", "frontendTreeSha256", "qualificationSha256")):
        raise ValueError("invalid artifact identity")
    if (manifest["releaseId"] != release_identity(manifest)
        or type(manifest["promotionSequence"]) is not int or manifest["promotionSequence"] < 1
        or type(manifest["recordingSchemaVersion"]) is not int or manifest["recordingSchemaVersion"] < 1
        or manifest["paidProvidersEnabled"] is not False
        or manifest["terminalFixSha"] != TERMINAL_FIX
        or any(manifest[k] is not True for k in GATES)):
        raise ValueError("unqualified release")
    return manifest


def current_release():
    protected(ROOT, directory=True)
    protected(ROOT / "releases", directory=True)
    link = ROOT / "current"
    info = link.lstat()
    if not stat.S_ISLNK(info.st_mode) or info.st_uid != 0:
        raise ValueError("untrusted current release link")
    target = link.resolve(strict=True)
    if target.parent != ROOT / "releases" or not DIGEST.fullmatch(target.name):
        raise ValueError("current release outside immutable layout")
    protected(target, directory=True)
    manifest = validate_manifest(read_json(target / "release.json"))
    if manifest["releaseId"] != target.name:
        raise ValueError("release directory identity mismatch")
    return target, manifest


def verify_installed_release(path, manifest):
    protected(path, directory=True)
    for entry in path.rglob("*"):
        protected(entry, directory=entry.is_dir())
    if validate_manifest(read_json(path / "release.json")) != manifest:
        raise ValueError("installed manifest differs from promotion")
    jar, frontend = artifact_digests(path)
    if (jar, frontend) != (manifest["jarSha256"], manifest["frontendTreeSha256"]):
        raise ValueError("installed artifact checksum mismatch")


class LinuxHost:
    def __init__(self):
        if sys.platform != "linux" or os.geteuid() != 0:
            raise RuntimeError("Linux root updater required")
        for marker in ("release-approved", "recording-approved", "network-approved"):
            protected(CONFIG / marker)
        protected(STATE, directory=True)
        self.policy = read_json(CONFIG / "updater-policy.json")
        if (set(self.policy) != {"channel", "minFreeBytes", "maxBackupAgeSeconds"}
            or self.policy["channel"] != "tolaria-stable"):
            raise ValueError("missing approved update policy")
        self.runtime_uid = pwd.getpwnam("argentum-play").pw_uid
        self.last_boot = None

    @contextlib.contextmanager
    def exclusive_lock(self):
        descriptor = os.open(STATE / "updater.lock", os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        try:
            info = os.fstat(descriptor)
            if info.st_uid != 0 or not stat.S_ISREG(info.st_mode) or info.st_mode & 0o077:
                raise ValueError("untrusted updater lock")
            fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
            yield
        finally:
            os.close(descriptor)

    def assert_no_unfinished_transaction(self):
        journal = STATE / "transactions.jsonl"
        if not journal.exists():
            return
        protected(journal)
        last = None
        with journal.open() as stream:
            for line in stream:
                if len(line) > 4096:
                    raise ValueError("invalid private transaction journal")
                last = json.loads(line)
        if last is not None and last.get("phase") != "completed":
            raise RuntimeError("unfinished transaction requires operator review")

    def verify_promotion(self, candidate):
        validate_manifest(candidate)
        if read_json(CONFIG / "promotions" / (candidate["releaseId"] + ".json")) != candidate:
            raise ValueError("promotion is not root approved")
        current = self.current_manifest()
        if candidate != current and candidate["promotionSequence"] <= current["promotionSequence"]:
            raise ValueError("non-forward promotion")

    def current_manifest(self):
        path, manifest = current_release()
        verify_installed_release(path, manifest)
        return manifest

    def stage_and_verify(self, candidate):
        verify_installed_release(ROOT / "releases" / candidate["releaseId"], candidate)
        receipt = read_json(CONFIG / "backup-health.json")
        status = self.status()
        volume = os.statvfs("/var/lib/commander-gym")
        decision = storage_decision(dict(receipt, canonicalRoot="/var/lib/commander-gym",
            freeBytes=volume.f_bavail * volume.f_frsize, durableBytes=status.get("durableBytes"),
            pendingRecordWrites=status.get("pendingRecordWrites"), recordingHealthy=status.get("recordingHealthy")),
            now=time.time(), min_free_bytes=self.policy["minFreeBytes"],
            max_backup_age_seconds=self.policy["maxBackupAgeSeconds"])
        if decision != "storage_ready":
            if decision == "pause_new_admission_low_storage":
                self.drain(status["bootId"])
            self.alert(decision)
            raise RuntimeError("storage or backup acceptance held")

    def smoke_closed(self, candidate):
        # Qualification is performed before installation by the approved stage owner.
        # Bind its complete private receipt to the authenticated promotion and bytes.
        receipt_path = CONFIG / "qualifications" / (candidate["releaseId"] + ".json")
        receipt = read_json(receipt_path)
        if hashlib.sha256(receipt_path.read_bytes()).hexdigest() != candidate["qualificationSha256"]:
            raise ValueError("qualification receipt checksum mismatch")
        if (receipt.get("engineSha") != candidate["engineSha"]
            or receipt.get("gymSha") != candidate["gymSha"]
            or receipt.get("jarSha256") != candidate["jarSha256"]
            or receipt.get("frontendTreeSha256") != candidate["frontendTreeSha256"]
            or receipt.get("cleanupVerified") is not True
            or any(receipt.get(k) is not True for k in GATES)):
            raise ValueError("staged isolated qualification incomplete")

    def rpc(self, operation, **fields):
        parent = CONTROL.parent
        info = parent.lstat()
        if (not stat.S_ISDIR(info.st_mode) or info.st_uid != self.runtime_uid
            or stat.S_IMODE(info.st_mode) != 0o700):
            raise ValueError("untrusted native control directory")
        info = CONTROL.lstat()
        if (not stat.S_ISSOCK(info.st_mode) or info.st_uid != self.runtime_uid
            or stat.S_IMODE(info.st_mode) != 0o600):
            raise ValueError("untrusted native control socket")
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
            client.settimeout(5)
            client.connect(str(CONTROL))
            _, uid, _ = struct.unpack("3i", client.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, 12))
            if uid != self.runtime_uid:
                raise ValueError("wrong native control peer")
            client.sendall((json.dumps(dict(protocol=1, op=operation, **fields)) + "\n").encode())
            raw = b""
            while not raw.endswith(b"\n"):
                chunk = client.recv(4096 - len(raw))
                if not chunk or len(raw) + len(chunk) >= 4096:
                    raise ValueError("incomplete native control reply")
                raw += chunk
            status = json.loads(raw)
        if type(status) is not dict or status.get("protocol") != 1 or status.get("ok") is not True:
            raise ValueError("native control rejected")
        return status

    def status(self): return self.rpc("status")
    def drain(self, boot): return self.rpc("drain", bootId=boot)

    def record(self, phase, current, candidate):
        self.append_private("transactions.jsonl", dict(phase=phase,
            currentRelease=current["releaseId"], candidateRelease=candidate["releaseId"], observedUnix=time.time()))

    def append_private(self, name, record):
        descriptor = os.open(STATE / name, os.O_WRONLY | os.O_APPEND | os.O_CREAT | os.O_NOFOLLOW, 0o600)
        try:
            info = os.fstat(descriptor)
            if info.st_uid != 0 or not stat.S_ISREG(info.st_mode) or info.st_mode & 0o077:
                raise ValueError("untrusted private operation journal")
            raw = (json.dumps(record, sort_keys=True, allow_nan=False) + "\n").encode()
            while raw:
                count = os.write(descriptor, raw)
                if count <= 0: raise OSError("private operation journal write failed")
                raw = raw[count:]
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
        directory = os.open(STATE, os.O_DIRECTORY)
        try: os.fsync(directory)
        finally: os.close(directory)

    def alert(self, reason):
        self.append_private("alerts.jsonl", dict(reason=reason, observedUnix=time.time()))

    def service(self, verb, unit):
        # Fixed unit names supplied by this class, never by downloaded metadata.
        subprocess.run(["/usr/bin/systemctl", verb, unit], check=True, timeout=90,
                       stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def switch(self, manifest):
        verify_installed_release(ROOT / "releases" / manifest["releaseId"], manifest)
        self.service("stop", "argentum-web.service")
        self.service("stop", "argentum-play.service")
        temporary = ROOT / "current.next"
        # Refuse leftover transaction links; do not unlink unknown objects.
        os.symlink("releases/" + manifest["releaseId"], temporary)
        os.replace(temporary, ROOT / "current")
        descriptor = os.open(ROOT, os.O_DIRECTORY)
        try: os.fsync(descriptor)
        finally: os.close(descriptor)

    def restart_closed(self):
        self.last_boot = None
        self.service("start", "argentum-play.service")
        self.service("start", "argentum-web.service")

    def health(self, manifest):
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            try:
                status = self.status()
                if (status.get("engineSha") == manifest["engineSha"]
                    and status.get("gymSha") == manifest["gymSha"]
                    and status.get("releaseId") == manifest["releaseId"]
                    and status.get("recordingSchemaVersion") == manifest["recordingSchemaVersion"]
                    and status.get("recoveryComplete") is True and status.get("recordingHealthy") is True
                    and status.get("acceptingNewGames") is False
                    and type(status.get("activeGames")) is int and status["activeGames"] == 0
                    and type(status.get("pendingActivities")) is int and status["pendingActivities"] == 0
                    and type(status.get("pendingRecordWrites")) is int and status["pendingRecordWrites"] == 0
                    and type(status.get("bootId")) is str and status["bootId"]
                    and type(status.get("observedUnix")) in (int, float)
                    and 0 <= time.time() - status["observedUnix"] <= 10):
                    # Facade and static bytes are checked without creating a game.
                    import urllib.request
                    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
                    with opener.open("http://127.0.0.1:8180/", timeout=2) as response:
                        if response.status != 200: raise ValueError("frontend health failed")
                    self.last_boot = status["bootId"]
                    return True
            except (OSError, ValueError):
                pass
            time.sleep(0.2)
        return False

    def resume(self, candidate):
        if self.last_boot is None: raise ValueError("no healthy closed boot")
        reply = self.rpc("resume", bootId=self.last_boot, releaseId=candidate["releaseId"])
        if reply.get("acceptingNewGames") is not True or reply.get("bootId") != self.last_boot:
            raise ValueError("admission opening unconfirmed")


def main():
    if sys.argv[1:] not in (["update"], ["admit-current"]):
        raise ValueError("unsupported updater operation")
    host = LinuxHost()
    if sys.argv[1:] == ["admit-current"]:
        with host.exclusive_lock():
            host.assert_no_unfinished_transaction()
            current = host.current_manifest()
            host.verify_promotion(current)
            state = host.status()
            if (state.get("acceptingNewGames") is True and state.get("releaseId") == current["releaseId"]
                and state.get("engineSha") == current["engineSha"] and state.get("gymSha") == current["gymSha"]):
                print("already_serving")
                return
            host.stage_and_verify(current)
            host.smoke_closed(current)
            if not host.health(current): raise RuntimeError("closed boot is not healthy")
            host.record("admission_intent", current, current)
            try:
                host.resume(current)
            except Exception:
                host.record("admission_uncertain_operator_review", current, current)
                raise
            host.record("completed", current, current)
            print("current_release_admitted")
        return
    protected(CONFIG / "promotions", directory=True)
    current = host.current_manifest()
    candidates = [validate_manifest(read_json(path)) for path in (CONFIG / "promotions").glob("*.json")]
    if len({item["promotionSequence"] for item in candidates}) != len(candidates):
        raise ValueError("ambiguous promotion order")
    newer = [item for item in candidates if item["promotionSequence"] > current["promotionSequence"]]
    if not newer:
        print("already_current")
        return
    candidate = max(newer, key=lambda item: item["promotionSequence"])
    print(Rollout(host, time.time).attempt(candidate))


if __name__ == "__main__":
    try: main()
    except Exception:
        # Fixed metadata only. Exception text can include private paths or transport details.
        print("native_update_held_operator_review", file=sys.stderr)
        sys.exit(1)
