#!/usr/bin/env python3
"""Install and manage a provenance-pinned macOS launchd service for gym-server."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import plistlib
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

LABEL = "io.argentum.gym-server"
DEFAULT_STATUS_URL = "http://127.0.0.1:8081/status"


def run(*args: str, cwd: Path | None = None, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=cwd,
        check=check,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )


def resolve_repo_root(candidate: str | None) -> Path:
    cwd = Path(candidate).expanduser().resolve() if candidate else Path.cwd()
    result = run("git", "rev-parse", "--show-toplevel", cwd=cwd)
    return Path(result.stdout.strip()).resolve()


def checkout_revision(repo_root: Path) -> str:
    return run("git", "rev-parse", "HEAD", cwd=repo_root).stdout.strip()


def assert_clean_tracked_checkout(repo_root: Path) -> None:
    dirty = run(
        "git",
        "status",
        "--porcelain",
        "--untracked-files=no",
        cwd=repo_root,
    ).stdout.strip()
    if dirty:
        raise RuntimeError(
            "tracked Argentum files are dirty; commit/stash them before installing the service"
        )


def resolve_java_home(explicit: str | None) -> Path:
    if explicit:
        java_home = Path(explicit).expanduser().resolve()
    elif os.environ.get("JAVA_HOME"):
        java_home = Path(os.environ["JAVA_HOME"]).expanduser().resolve()
    else:
        result = run("/usr/libexec/java_home", "-v", "21")
        java_home = Path(result.stdout.strip()).resolve()

    java = java_home / "bin" / "java"
    if not java.exists():
        raise RuntimeError(f"Java executable not found at {java}")
    return java_home


def default_paths(home: Path) -> dict[str, Path]:
    return {
        "plist": home / "Library" / "LaunchAgents" / f"{LABEL}.plist",
        "state_dir": home / "Library" / "Application Support" / "Argentum" / "gym-server",
        "log_dir": home / "Library" / "Logs" / "Argentum" / "gym-server",
    }


def render_plist(
    *,
    repo_root: Path,
    revision: str,
    java_home: Path,
    stdout_path: Path,
    stderr_path: Path,
) -> bytes:
    runner = repo_root / "scripts" / "run-gym-server-service.sh"
    payload: dict[str, Any] = {
        "Label": LABEL,
        "ProgramArguments": [
            "/bin/bash",
            str(runner),
            str(repo_root),
            revision,
        ],
        "WorkingDirectory": str(repo_root),
        "RunAtLoad": True,
        "KeepAlive": True,
        "ThrottleInterval": 10,
        "StandardOutPath": str(stdout_path),
        "StandardErrorPath": str(stderr_path),
        "EnvironmentVariables": {
            "ARGENTUM_BUILD_REVISION": revision,
            "JAVA_HOME": str(java_home),
            "PATH": (
                f"{java_home / 'bin'}:/opt/homebrew/bin:/usr/local/bin:"
                "/usr/bin:/bin:/usr/sbin:/sbin"
            ),
            "SERVER_ADDRESS": "127.0.0.1",
        },
    }
    return plistlib.dumps(payload, sort_keys=True)


def service_metadata(
    *,
    repo_root: Path,
    revision: str,
    java_home: Path,
    plist_path: Path,
    stdout_path: Path,
    stderr_path: Path,
) -> dict[str, Any]:
    return {
        "schema": "argentum-gym-server-launchd-v1",
        "label": LABEL,
        "repository": str(repo_root),
        "buildRevision": revision,
        "javaHome": str(java_home),
        "plist": str(plist_path),
        "stdoutLog": str(stdout_path),
        "stderrLog": str(stderr_path),
        "statusUrl": DEFAULT_STATUS_URL,
        "installedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
    }


def write_atomic(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_bytes(data)
    os.replace(temporary, path)


def launchd_target() -> str:
    return f"gui/{os.getuid()}/{LABEL}"


def install(args: argparse.Namespace) -> int:
    if sys.platform != "darwin":
        raise RuntimeError("install is supported only on macOS; use render for CI/inspection")

    repo_root = resolve_repo_root(args.repo)
    assert_clean_tracked_checkout(repo_root)
    revision = checkout_revision(repo_root)
    java_home = resolve_java_home(args.java_home)
    paths = default_paths(Path.home())
    plist_path = paths["plist"]
    state_dir = paths["state_dir"]
    log_dir = paths["log_dir"]
    stdout_path = log_dir / "stdout.log"
    stderr_path = log_dir / "stderr.log"

    log_dir.mkdir(parents=True, exist_ok=True)
    state_dir.mkdir(parents=True, exist_ok=True)
    plist = render_plist(
        repo_root=repo_root,
        revision=revision,
        java_home=java_home,
        stdout_path=stdout_path,
        stderr_path=stderr_path,
    )
    write_atomic(plist_path, plist)

    metadata = service_metadata(
        repo_root=repo_root,
        revision=revision,
        java_home=java_home,
        plist_path=plist_path,
        stdout_path=stdout_path,
        stderr_path=stderr_path,
    )
    write_atomic(
        state_dir / "service.json",
        (json.dumps(metadata, indent=2, sort_keys=True) + "\n").encode(),
    )

    run("launchctl", "bootout", launchd_target(), check=False)
    run("launchctl", "bootstrap", f"gui/{os.getuid()}", str(plist_path))
    run("launchctl", "enable", launchd_target())
    run("launchctl", "kickstart", "-k", launchd_target())

    print(json.dumps(metadata, indent=2, sort_keys=True))
    return 0


def render(args: argparse.Namespace) -> int:
    repo_root = Path(args.repo).expanduser().resolve()
    revision = args.revision
    java_home = Path(args.java_home).expanduser().resolve()
    stdout_path = Path(args.stdout).expanduser().resolve()
    stderr_path = Path(args.stderr).expanduser().resolve()
    sys.stdout.buffer.write(
        render_plist(
            repo_root=repo_root,
            revision=revision,
            java_home=java_home,
            stdout_path=stdout_path,
            stderr_path=stderr_path,
        )
    )
    return 0


def fetch_service_status() -> tuple[int, str]:
    try:
        with urllib.request.urlopen(DEFAULT_STATUS_URL, timeout=3) as response:
            return response.status, response.read().decode("utf-8")
    except (urllib.error.URLError, TimeoutError) as exc:
        return 0, str(exc)


def status(_args: argparse.Namespace) -> int:
    if sys.platform != "darwin":
        raise RuntimeError("status is supported only on macOS")

    launch = run("launchctl", "print", launchd_target(), check=False)
    http_status, body = fetch_service_status()
    payload = {
        "launchdLoaded": launch.returncode == 0,
        "launchd": launch.stdout if launch.returncode == 0 else launch.stderr,
        "httpStatus": http_status,
        "serviceStatus": body,
    }
    print(json.dumps(payload, indent=2, sort_keys=True))
    return 0 if launch.returncode == 0 and http_status == 200 else 1


def uninstall(_args: argparse.Namespace) -> int:
    if sys.platform != "darwin":
        raise RuntimeError("uninstall is supported only on macOS")

    paths = default_paths(Path.home())
    run("launchctl", "bootout", launchd_target(), check=False)
    paths["plist"].unlink(missing_ok=True)
    print(f"Uninstalled {LABEL}. Logs and service metadata were retained.")
    return 0


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest="command", required=True)

    install_parser = sub.add_parser("install", help="install/reload the launchd service")
    install_parser.add_argument("--repo", help="Argentum checkout; defaults to the current git checkout")
    install_parser.add_argument("--java-home", help="Java 21 home; defaults to JAVA_HOME or /usr/libexec/java_home -v 21")
    install_parser.set_defaults(func=install)

    render_parser = sub.add_parser("render", help="render a plist without touching launchd")
    render_parser.add_argument("--repo", required=True)
    render_parser.add_argument("--revision", required=True)
    render_parser.add_argument("--java-home", required=True)
    render_parser.add_argument("--stdout", required=True)
    render_parser.add_argument("--stderr", required=True)
    render_parser.set_defaults(func=render)

    status_parser = sub.add_parser("status", help="show launchd and HTTP service status")
    status_parser.set_defaults(func=status)

    uninstall_parser = sub.add_parser("uninstall", help="unload and remove the launch agent")
    uninstall_parser.set_defaults(func=uninstall)
    return p


def main() -> int:
    args = parser().parse_args()
    try:
        return args.func(args)
    except (RuntimeError, subprocess.CalledProcessError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
