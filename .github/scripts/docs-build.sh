#!/usr/bin/env bash
# Builds the docs of one checkout with Antora and writes Antora's JSON log.
#
#   docs-build.sh <checkout-dir> <playbook-source> <log-file>
#
# <playbook-source> is the published playbook (docs-playbook/docs-playbook.yml)
# whose AsciiDoc attributes and UI bundle the build uses; the content source is
# replaced with <checkout-dir> at its current commit (HEAD, attached or not), so
# the build checks exactly what is checked out there rather than the published
# branches. Antora itself must be on PATH (verify-docs.yaml installs a pinned
# version). This script never fails on Antora's result; docs-check.js decides
# from the log, and fails on any fatal entry or plain-text (crash) line.
set -euo pipefail

CHECKOUT_DIR=$(cd "$1" && pwd)
PLAYBOOK_SOURCE=$2
LOG_FILE=$3

WORK_DIR=$(mktemp -d)
PLAYBOOK="$WORK_DIR/playbook.yml"
: > "$LOG_FILE"
LOG_FILE=$(cd "$(dirname "$LOG_FILE")" && pwd)/$(basename "$LOG_FILE")

CONTENT_URL="$CHECKOUT_DIR" WORK_DIR="$WORK_DIR" LOG_FILE="$LOG_FILE" yq '
  .content.sources = [{"url": strenv(CONTENT_URL), "branches": "HEAD", "start_path": "docs"}]
  | del(.ui.supplemental_files)
  | .runtime = {"cache_dir": strenv(WORK_DIR) + "/cache",
                "log": {"level": "warn", "format": "json", "failure_level": "fatal",
                        "destination": {"file": strenv(LOG_FILE), "append": false}}}
  | .output = {"dir": strenv(WORK_DIR) + "/site"}
' "$PLAYBOOK_SOURCE" > "$PLAYBOOK"

# A fatal entry makes Antora exit non-zero; that entry is in the log, and
# docs-check.js fails on it. A crash before the logger is up goes to stderr
# instead, which is appended as plain text so that docs-check.js fails on it
# too.
# A non-zero exit is recorded as a plain-text line too, so that a failure
# with nothing in the log or on stderr still fails the check.
STATUS=0
antora --stacktrace "$PLAYBOOK" > /dev/null 2> "$WORK_DIR/stderr" || STATUS=$?
cat "$WORK_DIR/stderr" >> "$LOG_FILE"
if [ "$STATUS" -ne 0 ]; then
  echo "Antora exited with status $STATUS" >> "$LOG_FILE"
fi

echo "Antora build of $CHECKOUT_DIR finished: $(grep -c '' "$LOG_FILE" || true) log line(s) in $LOG_FILE"
