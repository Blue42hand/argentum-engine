#!/bin/bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <repo-root> <expected-revision>" >&2
  exit 64
fi

repo_root="$1"
expected_revision="$2"

cd "$repo_root"

actual_revision="$(git rev-parse HEAD)"
if [[ "$actual_revision" != "$expected_revision" ]]; then
  echo "Argentum checkout moved: expected $expected_revision, found $actual_revision. Reinstall the service before restarting it." >&2
  exit 78
fi

if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo "Argentum tracked files are dirty; refusing to start a service with ambiguous build provenance." >&2
  exit 78
fi

export ARGENTUM_BUILD_REVISION="$actual_revision"
export SERVER_ADDRESS="127.0.0.1"

exec ./gradlew --no-daemon :gym-server:bootRun
