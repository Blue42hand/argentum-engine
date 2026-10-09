import contextlib
import errno
import hashlib
import json
import os
from pathlib import Path
import socket
import stat
import sys
import tempfile
import threading
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import host_adapter as module
from native_server import launch_spec
from release_gate import GATES
from rollout import artifact_digests


def fixture(root, *, sequence=1):
    root.mkdir()
    (root / "web").mkdir()
    (root / "web/index.html").write_text("fixture")
    (root / "game-server.jar").write_bytes(b"public-test-jar")
    jar, web = artifact_digests(root)
    manifest = dict(channel="tolaria-stable", promotionSequence=sequence,
        engineSha="a" * 40, frontendEngineSha="a" * 40, gymSha="e" * 40,
        terminalFixSha=module.TERMINAL_FIX, jarSha256=jar, frontendTreeSha256=web,
        qualificationSha256="5" * 64, recordingSchemaVersion=1,
        paidProvidersEnabled=False, **dict.fromkeys(GATES, True))
    manifest["releaseId"] = module.release_identity(manifest)
    (root / "release.json").write_text(json.dumps(manifest))
    return manifest


class ManifestTests(unittest.TestCase):
    def test_all_fields_are_bound_and_unreviewed_schema_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            manifest = fixture(Path(tmp) / "release")
            self.assertEqual(module.validate_manifest(manifest), manifest)
            modified = dict(manifest, gymSha="d" * 40)
            with self.assertRaises(ValueError): module.validate_manifest(modified)
            modified = dict(manifest, extra="not permitted")
            with self.assertRaises(ValueError): module.validate_manifest(modified)
            modified = dict(manifest, paidProvidersEnabled=True)
            modified["releaseId"] = module.release_identity(modified)
            with self.assertRaises(ValueError): module.validate_manifest(modified)

    def test_launcher_uses_only_explicit_keyless_environment(self):
        manifest = dict(engineSha="a" * 40, releaseId="b" * 64, gymSha="e" * 40, recordingSchemaVersion=1)
        with patch.dict(os.environ, {"OPENAI_API_KEY": "synthetic-test-value",
                                     "JAVA_TOOL_OPTIONS": "synthetic-test-options"}):
            arguments, environment = launch_spec(Path("/fixed-release"), manifest)
        self.assertEqual(arguments[0], "/usr/bin/java")
        self.assertEqual(arguments[-1], "/fixed-release/game-server.jar")
        self.assertNotIn("OPENAI_API_KEY", environment)
        self.assertNotIn("JAVA_TOOL_OPTIONS", environment)
        self.assertEqual(environment["GAME_AI_MODE"], "engine")
        self.assertEqual(environment["NATIVE_LIFECYCLE_ENABLED"], "true")
        self.assertEqual(environment["NATIVE_GYM_SHA"], manifest["gymSha"])
        self.assertIn("-Dlogging.level.com.wingedsheep.gameserver.session.ZombieSessionSweeper=WARN", arguments)

    def test_trusted_json_still_rejects_duplicate_keys(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "duplicate.json"
            path.write_text('{"channel":"one","channel":"two"}')
            with patch.object(module, "protected"):
                with self.assertRaises(ValueError): module.read_json(path)

    def test_group_writable_installed_file_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "untrusted"
            path.write_text("test")
            path.chmod(0o660)
            with self.assertRaises(ValueError): module.protected(path)


class HostTests(unittest.TestCase):
    def test_periodic_admission_checks_storage_even_while_serving(self):
        current = dict(releaseId="a", engineSha="b", gymSha="c")
        events = []
        host = SimpleNamespace(exclusive_lock=contextlib.nullcontext,
            assert_no_unfinished_transaction=lambda: None,
            current_manifest=lambda: current, verify_promotion=lambda value: None,
            stage_and_verify=lambda value: events.append("storage"),
            status=lambda: dict(current, acceptingNewGames=True))
        with patch.object(module, "LinuxHost", return_value=host), patch.object(sys, "argv", ["host_adapter", "admit-current"]):
            module.main()
        self.assertEqual(events, ["storage"])

    def test_admission_timer_preserves_acknowledged_update_drain(self):
        events = []
        host = SimpleNamespace(exclusive_lock=contextlib.nullcontext,
            assert_no_unfinished_transaction=lambda: None,
            current_manifest=lambda: {}, verify_promotion=lambda value: None,
            stage_and_verify=lambda value: events.append("storage"),
            status=lambda: dict(acceptingNewGames=False, drainAcknowledged=True),
            resume=lambda value: events.append("resume"))
        with patch.object(module, "LinuxHost", return_value=host), patch.object(sys, "argv", ["host_adapter", "admit-current"]):
            module.main()
        self.assertEqual(events, ["storage"])

    def test_unchanged_update_still_checks_storage(self):
        with tempfile.TemporaryDirectory() as tmp:
            config = Path(tmp)
            (config / "promotions").mkdir()
            events = []
            host = SimpleNamespace(exclusive_lock=contextlib.nullcontext,
                assert_no_unfinished_transaction=lambda: None,
                current_manifest=lambda: dict(promotionSequence=1),
                verify_promotion=lambda value: None,
                stage_and_verify=lambda value: events.append("storage"))
            with patch.object(module, "CONFIG", config), patch.object(module, "protected"), \
                patch.object(module, "LinuxHost", return_value=host), patch.object(sys, "argv", ["host_adapter", "update"]):
                module.main()
            self.assertEqual(events, ["storage"])

    def test_atomic_pair_switch_stops_both_units_before_link_change(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "releases").mkdir()
            manifest = fixture(root / "releases/staging")
            release = root / "releases" / manifest["releaseId"]
            (root / "releases/staging").rename(release)
            (root / "current").symlink_to("releases/previous")
            host = object.__new__(module.LinuxHost)
            events = []
            def service(verb, unit):
                self.assertEqual(os.readlink(root / "current"), "releases/previous")
                events.append((verb, unit))
            host.service = service
            with patch.object(module, "ROOT", root), patch.object(module, "protected"):
                host.switch(manifest)
            self.assertEqual(events, [("stop", "argentum-web.service"), ("stop", "argentum-play.service")])
            self.assertEqual((root / "current").resolve(), release.resolve())
            self.assertFalse((root / "current.next").exists())

    def test_installed_byte_tampering_and_links_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "release"
            manifest = fixture(root)
            with patch.object(module, "protected"):
                module.verify_installed_release(root, manifest)
                (root / "web/index.html").write_text("modified")
                with self.assertRaises(ValueError): module.verify_installed_release(root, manifest)
                (root / "web/link").symlink_to("/untrusted")
                with self.assertRaises(ValueError): artifact_digests(root)

    def test_low_storage_closes_admission_and_alerts_without_cleanup(self):
        host = object.__new__(module.LinuxHost)
        host.policy = dict(minFreeBytes=50, maxBackupAgeSeconds=20)
        host.status = lambda: dict(bootId="boot", durableBytes=10, pendingRecordWrites=0, recordingHealthy=True)
        events = []
        host.drain = lambda boot: events.append(("drain", boot))
        host.alert = lambda reason: events.append(("alert", reason))
        with patch.object(module, "verify_installed_release"), patch.object(module, "read_json",
            return_value=dict(lastVerifiedBackupUnix=100, backupCoversRecorderManifest=True)), \
            patch.object(module.os, "statvfs", return_value=SimpleNamespace(f_bavail=49, f_frsize=1)), \
            patch.object(module.time, "time", return_value=105):
            with self.assertRaises(RuntimeError): host.stage_and_verify(dict(releaseId="b" * 64))
        self.assertEqual(events, [("drain", "boot"), ("alert", "pause_new_admission_low_storage")])

    def test_unfinished_or_rolled_back_journal_holds_future_updates(self):
        with tempfile.TemporaryDirectory() as tmp:
            state = Path(tmp)
            path = state / "transactions.jsonl"
            host = object.__new__(module.LinuxHost)
            with patch.object(module, "STATE", state), patch.object(module, "protected"):
                host.assert_no_unfinished_transaction()
                for phase in ("activation_intent", "admission_intent", "rolled_back_closed"):
                    path.write_text(json.dumps(dict(phase=phase)) + "\n")
                    with self.assertRaises(RuntimeError): host.assert_no_unfinished_transaction()
                path.write_text(json.dumps(dict(phase="completed")) + "\n")
                host.assert_no_unfinished_transaction()

    @unittest.skipUnless(sys.platform == "linux", "Linux peer credentials")
    def test_real_private_client_verifies_linux_peer(self):
        with tempfile.TemporaryDirectory(dir="/tmp") as tmp:
            directory = Path(tmp)
            directory.chmod(0o700)
            path = directory / "control.sock"
            with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as listener:
                listener.bind(str(path))
                path.chmod(0o600)
                listener.listen(1)
                def serve():
                    with listener.accept()[0] as client:
                        client.recv(4096)
                        client.sendall(b'{"protocol":1,"ok":true,"activeGames":0}\n')
                worker = threading.Thread(target=serve)
                worker.start()
                host = object.__new__(module.LinuxHost)
                host.runtime_uid = os.getuid()
                with patch.object(module, "CONTROL", path):
                    self.assertEqual(host.rpc("status")["activeGames"], 0)
                worker.join(5)
                self.assertFalse(worker.is_alive())





class LockBoundaryTests(unittest.TestCase):
    def host(self): return object.__new__(module.LinuxHost)

    def test_only_busy_flock_is_classified_and_descriptor_always_closed(self):
        for code in {errno.EAGAIN, errno.EWOULDBLOCK, errno.EACCES, errno.EIO}:
            with self.subTest(errno=code), patch.object(module.os, 'open', return_value=51), patch.object(module.os, 'fstat', return_value=SimpleNamespace(st_uid=0, st_mode=stat.S_IFREG | 0o600)), patch.object(module.os, 'close') as close, patch.object(module.fcntl, 'flock', side_effect=OSError(code, 'fixture')):
                expected = module.UpdaterLockBusy if code in (errno.EAGAIN, errno.EWOULDBLOCK) else OSError
                with self.assertRaises(expected):
                    with self.host().exclusive_lock(): self.fail('busy lock entered')
                close.assert_called_once_with(51)

    def test_open_and_fstat_failures_are_not_busy(self):
        error = BlockingIOError(errno.EAGAIN, 'fixture')
        with patch.object(module.os, 'open', side_effect=error):
            with self.assertRaises(BlockingIOError):
                with self.host().exclusive_lock(): self.fail('open failed')
        with patch.object(module.os, 'open', return_value=51), patch.object(module.os, 'fstat', side_effect=error), patch.object(module.os, 'close') as close:
            with self.assertRaises(BlockingIOError):
                with self.host().exclusive_lock(): self.fail('stat failed')
            close.assert_called_once_with(51)

    def test_body_failure_keeps_original_exception_and_closes_descriptor(self):
        error = BlockingIOError(errno.EAGAIN, 'body fixture')
        with patch.object(module.os, 'open', return_value=51), patch.object(module.os, 'fstat', return_value=SimpleNamespace(st_uid=0, st_mode=stat.S_IFREG | 0o600)), patch.object(module.os, 'close') as close, patch.object(module.fcntl, 'flock') as flock:
            with self.assertRaises(BlockingIOError) as caught:
                with self.host().exclusive_lock(): raise error
            self.assertIs(caught.exception, error)
            flock.assert_called_once_with(51, module.fcntl.LOCK_EX | module.fcntl.LOCK_NB)
            close.assert_called_once_with(51)

    def test_untrusted_lock_never_calls_flock(self):
        with patch.object(module.os, 'open', return_value=51), patch.object(module.os, 'fstat', return_value=SimpleNamespace(st_uid=999, st_mode=stat.S_IFREG | 0o600)), patch.object(module.os, 'close'), patch.object(module.fcntl, 'flock') as flock:
            with self.assertRaises(ValueError):
                with self.host().exclusive_lock(): self.fail('untrusted lock entered')
            flock.assert_not_called()


class RealFlockTests(unittest.TestCase):
    def test_another_descriptor_defers_then_release_allows_entry(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'updater.lock'
            fd = os.open(path, os.O_RDWR | os.O_CREAT, 0o600)
            module.fcntl.flock(fd, module.fcntl.LOCK_EX | module.fcntl.LOCK_NB)
            original_fstat = os.fstat
            def root_stat(descriptor):
                info = original_fstat(descriptor)
                return SimpleNamespace(st_uid=0, st_mode=info.st_mode)
            host = object.__new__(module.LinuxHost)
            try:
                with patch.object(module, 'STATE', Path(tmp)), patch.object(module.os, 'fstat', side_effect=root_stat):
                    with self.assertRaises(module.UpdaterLockBusy):
                        with host.exclusive_lock(): self.fail('contended lock entered')
                    module.fcntl.flock(fd, module.fcntl.LOCK_UN)
                    with host.exclusive_lock(): pass
            finally: os.close(fd)


if __name__ == "__main__": unittest.main()
