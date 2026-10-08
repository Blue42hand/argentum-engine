import unittest
from storage_health import storage_decision

class StorageTests(unittest.TestCase):
    def setUp(self):
        self.state = dict(canonicalRoot="/var/lib/commander-gym", freeBytes=100,
                          durableBytes=200, pendingRecordWrites=0,
                          backupCoversRecorderManifest=True,
                          lastVerifiedBackupUnix=100, recordingHealthy=True)
    def decision(self):
        return storage_decision(self.state, now=105, min_free_bytes=50,
                                max_backup_age_seconds=20)
    def test_healthy(self): self.assertEqual(self.decision(), "storage_ready")
    def test_low_storage_pauses_without_deletion(self):
        self.state["freeBytes"] = 49
        self.assertEqual(self.decision(), "pause_new_admission_low_storage")
    def test_old_or_uncovered_backup_blocks(self):
        self.state["lastVerifiedBackupUnix"] = 80
        self.assertEqual(self.decision(), "hold_backup_stale")
        self.state.update(lastVerifiedBackupUnix=100, backupCoversRecorderManifest=False)
        self.assertEqual(self.decision(), "hold_backup_coverage")
    def test_alternate_archive_blocks(self):
        self.state["canonicalRoot"] = "/var/lib/argentum-play"
        self.assertEqual(self.decision(), "hold_wrong_recording_root")
    def test_missing_or_invalid_counters_block(self):
        self.state["freeBytes"] = False
        self.assertEqual(self.decision(), "hold_missing_storage_evidence")

if __name__ == "__main__": unittest.main()
