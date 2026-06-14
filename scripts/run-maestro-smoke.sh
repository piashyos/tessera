#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FLOW_PATH="${1:-$ROOT_DIR/.maestro/showcase-smoke.yaml}"

if ! command -v maestro >/dev/null 2>&1; then
  echo "Maestro CLI is not installed. Install it first, then rerun this script." >&2
  exit 1
fi

"$ROOT_DIR/scripts/run-debug-on-emulator.sh"
maestro test "$FLOW_PATH"
