#!/usr/bin/env bash
# Playwright tests of the schema usage dashboard against a Grafana that is already running (see tests/ui/run-ui.sh for the
# options, the environment and the output). Starts nothing; reads the run state of the last ./demo.sh run.
set -euo pipefail
exec "$(cd "$(dirname "$0")" && pwd)/tests/ui/run-ui.sh" tests "$@"
