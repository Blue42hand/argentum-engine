import unittest
from release_gate import GATES, rollout_decision


class ReleaseGateTests(unittest.TestCase):
    def setUp(self):
        self.current = {"engineSha": "a" * 40, "recordingSchemaVersion": 1,
                        "releaseId": "3" * 64, "gymSha": "e" * 40,
                        "jarSha256": "1" * 64, "frontendTreeSha256": "2" * 64}
        self.candidate = {"channel": "tolaria-stable", "engineSha": "b" * 40,
                          "releaseId": "4" * 64,
                          "gymSha": "e" * 40,
                          "frontendEngineSha": "b" * 40, "jarSha256": "c" * 64,
                          "frontendTreeSha256": "d" * 64, "recordingSchemaVersion": 1,
                          "paidProvidersEnabled": False, **dict.fromkeys(GATES, True)}
        self.state = {"engineSha": "a" * 40, "observedUnix": 100,
                      "releaseId": "3" * 64, "gymSha": "e" * 40,
                      "bootId": "boot-a", "recoveryComplete": True,
                      "drainAcknowledged": True,
                      "recordingSchemaVersion": 1, "recordingDrainComplete": True,
                      "acceptingNewGames": False, "activeGames": 0,
                      "pendingRecordWrites": 0, "recordingHealthy": True}
        self.state["pendingActivities"] = 0

    def decision(self, **kw):
        return rollout_decision(self.candidate, self.current, self.state,
                                now=105, promotion_verified=True, **kw)

    def test_trusted_idle_candidate_eligible(self):
        self.assertEqual(self.decision(), "eligible_after_verified_drain")

    def test_paused_human_game_still_blocks(self):
        self.state['activeGames'] = 1
        self.assertEqual(self.decision(), "defer_active_games_or_unflushed_records")

    def test_unflushed_terminal_data_blocks(self):
        self.state['pendingRecordWrites'] = 1
        self.assertEqual(self.decision(), "defer_active_games_or_unflushed_records")

    def test_new_games_require_drain_not_restart(self):
        self.state['acceptingNewGames'] = True
        self.assertEqual(self.decision(), "request_drain")

    def test_stale_or_wrong_release_status_blocks(self):
        self.state['observedUnix'] = 90
        self.assertEqual(self.decision(), "hold_stale_status")
        self.state.update(observedUnix=100, engineSha='e' * 40)
        self.assertEqual(self.decision(), "hold_status_release_mismatch")

    def test_boolean_count_is_not_zero_game_evidence(self):
        self.state['activeGames'] = False
        self.assertEqual(self.decision(), "hold_invalid_counts")

    def test_private_or_unhealthy_recording_blocks(self):
        self.state['recordingHealthy'] = False
        self.assertEqual(self.decision(), "hold_recording_failure")

    def test_no_unreviewed_schema_migration(self):
        self.candidate['recordingSchemaVersion'] = 2
        self.assertEqual(self.decision(), "hold_recording_migration_requires_review")

    def test_missing_current_or_recording_identity_blocks(self):
        self.candidate.pop('recordingSchemaVersion')
        self.current.pop('recordingSchemaVersion')
        self.assertEqual(self.decision(), "hold_recording_schema_missing")
        self.current.pop('engineSha')
        self.assertEqual(self.decision(), "hold_current_identity")

    def test_matching_frontend_and_no_paid_providers_required(self):
        self.candidate['frontendEngineSha'] = 'f' * 40
        self.assertEqual(self.decision(), "hold_mismatched_frontend")
        self.candidate['frontendEngineSha'] = self.candidate['engineSha']
        self.candidate['paidProvidersEnabled'] = True
        self.assertEqual(self.decision(), "hold_paid_provider_config")

    def test_claimed_checks_do_not_replace_verified_promotion(self):
        self.assertEqual(rollout_decision(self.candidate, self.current, self.state, now=105),
                         "hold_unverified_promotion")


if __name__ == '__main__':
    unittest.main()
