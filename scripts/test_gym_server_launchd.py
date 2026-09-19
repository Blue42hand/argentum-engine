import importlib.util
import plistlib
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("gym_server_launchd.py")
SPEC = importlib.util.spec_from_file_location("gym_server_launchd", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)


class GymServerLaunchdTest(unittest.TestCase):
    def test_plist_pins_revision_and_loopback(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            repo = root / "argentum-engine"
            java_home = root / "jdk-21"
            stdout = root / "logs" / "stdout.log"
            stderr = root / "logs" / "stderr.log"
            revision = "a" * 40

            payload = plistlib.loads(
                MODULE.render_plist(
                    repo_root=repo,
                    revision=revision,
                    java_home=java_home,
                    stdout_path=stdout,
                    stderr_path=stderr,
                )
            )

            self.assertEqual(payload["Label"], MODULE.LABEL)
            self.assertTrue(payload["RunAtLoad"])
            self.assertTrue(payload["KeepAlive"])
            self.assertEqual(payload["WorkingDirectory"], str(repo))
            self.assertEqual(payload["EnvironmentVariables"]["ARGENTUM_BUILD_REVISION"], revision)
            self.assertEqual(payload["EnvironmentVariables"]["SERVER_ADDRESS"], "127.0.0.1")
            self.assertEqual(payload["EnvironmentVariables"]["JAVA_HOME"], str(java_home))
            self.assertEqual(payload["StandardOutPath"], str(stdout))
            self.assertEqual(payload["StandardErrorPath"], str(stderr))
            self.assertEqual(
                payload["ProgramArguments"],
                [
                    "/bin/bash",
                    str(repo / "scripts" / "run-gym-server-service.sh"),
                    str(repo),
                    revision,
                ],
            )

    def test_metadata_records_reproducibility_fields(self):
        root = Path("/tmp/example")
        metadata = MODULE.service_metadata(
            repo_root=root,
            revision="b" * 40,
            java_home=Path("/tmp/jdk"),
            plist_path=Path("/tmp/agent.plist"),
            stdout_path=Path("/tmp/stdout.log"),
            stderr_path=Path("/tmp/stderr.log"),
        )

        self.assertEqual(metadata["schema"], "argentum-gym-server-launchd-v1")
        self.assertEqual(metadata["buildRevision"], "b" * 40)
        self.assertEqual(metadata["repository"], str(root))
        self.assertEqual(metadata["statusUrl"], "http://127.0.0.1:8081/status")
        self.assertIn("installedAt", metadata)

    def test_default_paths_are_deterministic(self):
        home = Path("/Users/example")
        paths = MODULE.default_paths(home)
        self.assertEqual(
            paths["plist"],
            home / "Library" / "LaunchAgents" / f"{MODULE.LABEL}.plist",
        )
        self.assertEqual(
            paths["state_dir"],
            home / "Library" / "Application Support" / "Argentum" / "gym-server",
        )
        self.assertEqual(
            paths["log_dir"],
            home / "Library" / "Logs" / "Argentum" / "gym-server",
        )


if __name__ == "__main__":
    unittest.main()
