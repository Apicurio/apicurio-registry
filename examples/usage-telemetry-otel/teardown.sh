#!/usr/bin/env bash
# Stops and removes this example's containers and network. The telemetry history (the Prometheus and
# Grafana volumes) and the run states are kept, so a later ./setup.sh continues where this stopped.
#
# Usage: ./teardown.sh [--project NAME] [--purge]
#   --project NAME  the Compose project to stop (default usage-telemetry-otel, or COMPOSE_PROJECT_NAME)
#   --purge         also DELETE this project's volumes (all Prometheus history and Grafana state) and this
#                   project's run states in .local/projects/<project>/; this cannot be undone
# Only resources of the one Compose project are touched: no other container, volume, image or network,
# and no global prune.
set -euo pipefail
cd "$(dirname "$0")"
# shellcheck source=lib.sh
. ./lib.sh

usage() { sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; }

purge=false
while [ $# -gt 0 ]; do
  case "$1" in
    --project) [ $# -ge 2 ] || die "--project needs a name"; PROJECT=$2; shift 2 ;;
    --purge) purge=true; shift ;;
    -h | --help) usage; exit 0 ;;
    *) usage >&2; die "unknown option '$1'" ;;
  esac
done
validate_project
export COMPOSE_PROJECT_NAME=$PROJECT

detect_runtime
compose_cmd

if $purge; then
  # Decide, and refuse a linked state path, before anything is removed; this creates nothing
  have_state=false
  if state_purgeable; then have_state=true; fi
  info "PURGE requested: removing the containers of project '$PROJECT' and deleting its volumes (Prometheus history, Grafana state)."
  run_bounded 300 "${COMPOSE_CMD[@]}" down --volumes --remove-orphans || die "compose down --volumes failed; nothing else was touched"
  if $have_state; then
    # Only this project's own run states, only files
    state=$(state_dir)
    rm -f "$state/run-state.json"
    rm -f "$state"/runs/*.json
    info "The run states of project '$PROJECT' in $state were removed too, because the history they describe is gone."
  fi
  info "Purged. Volumes of project '$PROJECT' that remain:"
else
  run_bounded 300 "${COMPOSE_CMD[@]}" down --remove-orphans || die "compose down failed; nothing else was touched"
  info "Stopped project '$PROJECT'. The telemetry volumes were kept (./teardown.sh --purge deletes them). Volumes:"
fi
if ! project_volumes; then
  die "could not list the volumes of project '$PROJECT' (the query failed or timed out after ${PROBE_TIMEOUT}s)"
fi
printf '%s\n' "$Q_OUT" | awk 'NF { print "  " $0 }'
if ! count_project_containers; then
  die "could not list the containers of project '$PROJECT' (the query failed or timed out after ${PROBE_TIMEOUT}s); do not assume they are gone"
fi
left=$Q_OUT
[ "$left" = 0 ] || die "$left container(s) of project '$PROJECT' are still present"
info "No containers of project '$PROJECT' remain."
