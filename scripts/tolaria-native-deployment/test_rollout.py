import contextlib
import tempfile
import unittest
from pathlib import Path
from rollout import Rollout, artifact_digests
import test_release_gate


class Host:
    def __init__(self, fixture):
        self.current = fixture.current
        self.state = dict(fixture.state)
        self.events = []
        self.health_values = [True]
        self.fail_resume = False
        self.status_calls = 0
        self.race = False
    def exclusive_lock(self): return contextlib.nullcontext()
    def assert_no_unfinished_transaction(self): pass
    def verify_promotion(self, candidate): pass
    def current_manifest(self): return self.current
    def stage_and_verify(self, candidate): self.events.append("stage")
    def smoke_closed(self, candidate): self.events.append("smoke")
    def status(self):
        self.status_calls += 1
        result = dict(self.state)
        if self.race and self.status_calls == 2: result["activeGames"] = 1
        return result
    def drain(self, boot):
        self.events.append("drain")
        self.state.update(acceptingNewGames=False, drainAcknowledged=True)
    def record(self, phase, old, new): self.events.append(phase)
    def switch(self, manifest): self.events.append("switch:" + manifest["engineSha"][:1])
    def restart_closed(self): self.events.append("restart_closed")
    def health(self, manifest): return self.health_values.pop(0)
    def resume(self, manifest):
        self.events.append("resume")
        if self.fail_resume: raise RuntimeError("uncertain admission")


class TransactionTests(unittest.TestCase):
    def setUp(self):
        self.fixture = test_release_gate.ReleaseGateTests()
        self.fixture.setUp()
        self.host = Host(self.fixture)
    def run_attempt(self): return Rollout(self.host, lambda: 105).attempt(self.fixture.candidate)
    def test_success_opens_only_after_health(self):
        self.assertEqual(self.run_attempt(), "activated")
        self.assertLess(self.host.events.index("healthy_closed"), self.host.events.index("resume"))
    def test_drain_then_recheck(self):
        self.host.state["acceptingNewGames"] = True
        self.assertEqual(self.run_attempt(), "activated")
        self.assertIn("drain", self.host.events)
    def test_active_game_never_switches(self):
        self.host.state["activeGames"] = 1
        self.assertEqual(self.run_attempt(), "defer_active_games_or_unflushed_records")
        self.assertNotIn("switch:b", self.host.events)
    def test_final_race_never_switches(self):
        self.host.race = True
        self.assertEqual(self.run_attempt(), "defer_active_games_or_unflushed_records")
        self.assertNotIn("activation_intent", self.host.events)
    def test_failed_health_restores_previous_pair_closed(self):
        self.host.health_values = [False, True]
        self.assertEqual(self.run_attempt(), "rolled_back_closed")
        self.assertIn("switch:a", self.host.events)
        self.assertNotIn("resume", self.host.events)
    def test_failed_rollback_requires_operator(self):
        self.host.health_values = [False, False]
        with self.assertRaises(RuntimeError): self.run_attempt()
        self.assertEqual(self.host.events[-1], "operator_review_required")
    def test_uncertain_resume_never_rolls_back(self):
        self.host.fail_resume = True
        with self.assertRaises(RuntimeError): self.run_attempt()
        self.assertNotIn("switch:a", self.host.events)
        self.assertEqual(self.host.events[-1], "admission_uncertain_operator_review")
    def test_recovery_and_explicit_drain_required(self):
        self.host.state["recoveryComplete"] = False
        self.assertEqual(self.run_attempt(), "hold_recovery_incomplete")
        self.host.state.update(recoveryComplete=True, drainAcknowledged=False)
        self.assertEqual(self.run_attempt(), "hold_unacknowledged_drain")
    def test_same_source_changed_bytes_not_skipped(self):
        self.fixture.candidate["engineSha"] = self.fixture.current["engineSha"]
        self.fixture.candidate["frontendEngineSha"] = self.fixture.current["engineSha"]
        self.assertEqual(self.run_attempt(), "activated")


class ArtifactTests(unittest.TestCase):
    def test_hashes_change_and_links_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "game-server.jar").write_bytes(b"jar")
            (root / "web").mkdir()
            index = root / "web/index.html"
            index.write_text("one")
            first = artifact_digests(root)
            index.write_text("two")
            self.assertNotEqual(first, artifact_digests(root))
            (root / "web/link").symlink_to(index)
            with self.assertRaises(ValueError): artifact_digests(root)


if __name__ == "__main__": unittest.main()
