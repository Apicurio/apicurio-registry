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
# version). The build never fails on a warning or error here; docs-check.js
# decides by comparing two logs. It exits non-zero only if Antora could not run.
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
# docs-check.js reports it, so the exit status is not used. A crash before the
# logger is up goes to stderr instead, which is appended as plain text so that
# docs-check.js reports it as an error too.
antora --stacktrace "$PLAYBOOK" > /dev/null 2> "$WORK_DIR/stderr" || true
cat "$WORK_DIR/stderr" >> "$LOG_FILE"

echo "Antora build of $CHECKOUT_DIR finished: $(grep -c '' "$LOG_FILE" || true) log line(s) in $LOG_FILE"
