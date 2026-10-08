"""Launch only the verified current pair with an explicit, keyless environment."""
import os
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from host_adapter import current_release, verify_installed_release


def launch_spec(path, manifest):
    environment = {
        "PATH": "/usr/bin:/bin", "LANG": "C.UTF-8",
        "SERVER_ADDRESS": "127.0.0.1", "SERVER_PORT": "18080",
        "APP_VERSION": manifest["engineSha"], "NATIVE_RELEASE_ID": manifest["releaseId"],
        "NATIVE_GYM_SHA": manifest["gymSha"], "NATIVE_LIFECYCLE_ENABLED": "true",
        "NATIVE_RECORDING_SCHEMA_VERSION": str(manifest["recordingSchemaVersion"]),
        "NATIVE_LIFECYCLE_SOCKET": "/run/argentum-play/lifecycle.sock", "NATIVE_UPDATER_USER": "root",
        "GAME_AI_MODE": "engine", "GAME_AI_ENABLED": "true",
        "GAME_DEBUG_MODE": "false", "GAME_DEV_ENDPOINTS_ENABLED": "false",
        "GAME_AI_INSIGHT_ENABLED": "false", "GAME_EASTER_EGGS_ENABLED": "false",
        "GAME_TOURNAMENT_SIMULATE_AI_MATCHES": "false",
        "ACCOUNTS_ENABLED": "false", "CACHE_REDIS_ENABLED": "false",
    }
    arguments = ["/usr/bin/java", "-Xms512m", "-Xmx6g",
        "-Dlogging.level.com.wingedsheep.gameserver.handler.ConnectionHandler=WARN",
        "-Dlogging.level.com.wingedsheep.gameserver.session.ZombieSessionSweeper=WARN",
        "-Dlogging.level.com.wingedsheep.gameserver.websocket.GameWebSocketHandler=INFO",
        "-Dlogging.level.com.wingedsheep.gameserver.persistence.SessionRecoveryService=WARN",
        "-jar", str(path / "game-server.jar")]
    return arguments, environment


def main():
    if sys.argv[1:]: raise ValueError("native launcher accepts no mutable arguments")
    path, manifest = current_release()
    verify_installed_release(path, manifest)
    arguments, environment = launch_spec(path, manifest)
    os.execve(arguments[0], arguments, environment)


if __name__ == "__main__":
    try: main()
    except Exception:
        print("native_start_held_identity_or_permissions", file=sys.stderr)
        sys.exit(1)
