#!/usr/bin/env bash
# Screenshots of the schema usage dashboard for the last ./demo.sh run, taken by the same Playwright flows and assertions as
# ./test-ui.sh (see tests/ui/run-ui.sh for the options, the environment and the output). Starts nothing; writes overview.png
# and filtered.png into a fresh directory.
set -euo pipefail
exec "$(cd "$(dirname "$0")" && pwd)/tests/ui/run-ui.sh" screenshots "$@"
