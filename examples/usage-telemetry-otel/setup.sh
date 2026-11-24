#!/usr/bin/env bash
# Starts the four-component usage monitoring stack (Registry, OpenTelemetry Collector, Prometheus,
# Grafana) and waits until it works end to end: every service answers, Prometheus scrapes the Collector,
# Grafana reaches Prometheus, and the Registry's exports arrive.
#
# Usage: ./setup.sh [--project NAME] [--registry-port N] [--prometheus-port N] [--grafana-port N]
#                   [--collector-health-port N]
# Environment: REGISTRY_IMAGE (default apicurio/apicurio-registry:latest-snapshot), CONTAINER_RUNTIME
#              (podman or docker), the *_PORT variables, OTEL_COLLECTOR_CONFIG, PROMETHEUS_CONFIG.
# Safe to run again: a service that is already running is left as it is. It never installs software,
# starts a container machine, or touches anything outside this Compose project.
set -euo pipefail
cd "$(dirname "$0")"
# shellcheck source=lib.sh
. ./lib.sh

# Seconds each stage may take before the setup fails (and prints the logs)
UP_TIMEOUT=${UP_TIMEOUT:-900}
VALIDATE_TIMEOUT=${VALIDATE_TIMEOUT:-300}
REGISTRY_READY_TIMEOUT=${REGISTRY_READY_TIMEOUT:-180}
READY_TIMEOUT=${READY_TIMEOUT:-90}

usage() { sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; }

registry_image_given=${REGISTRY_IMAGE:+yes}
while [ $# -gt 0 ]; do
  case "$1" in
    --project) [ $# -ge 2 ] || die "--project needs a name"; PROJECT=$2; shift 2 ;;
    --registry-port) [ $# -ge 2 ] || die "--registry-port needs a number"; REGISTRY_PORT=$2; shift 2 ;;
    --prometheus-port) [ $# -ge 2 ] || die "--prometheus-port needs a number"; PROMETHEUS_PORT=$2; shift 2 ;;
    --grafana-port) [ $# -ge 2 ] || die "--grafana-port needs a number"; GRAFANA_PORT=$2; shift 2 ;;
    --collector-health-port) [ $# -ge 2 ] || die "--collector-health-port needs a number"; COLLECTOR_HEALTH_PORT=$2; shift 2 ;;
    -h | --help) usage; exit 0 ;;
    *) usage >&2; die "unknown option '$1'" ;;
  esac
done

need_commands curl jq
validate_project
for p in REGISTRY_PORT PROMETHEUS_PORT GRAFANA_PORT COLLECTOR_HEALTH_PORT; do
  v=${!p}
  case "$v" in
    *[!0-9]* | "") die "$p must be a port number, got '$v'" ;;
  esac
  if [ "$v" -lt 1 ] || [ "$v" -gt 65535 ]; then die "$p must be between 1 and 65535, got '$v'"; fi
done
# The URLs follow the ports chosen above (lib.sh computed them before the options were read)
REGISTRY_URL=http://127.0.0.1:$REGISTRY_PORT
API=$REGISTRY_URL/apis/registry/v3
PROMETHEUS_URL=http://127.0.0.1:$PROMETHEUS_PORT
GRAFANA_URL=http://127.0.0.1:$GRAFANA_PORT
COLLECTOR_HEALTH_URL=http://127.0.0.1:$COLLECTOR_HEALTH_PORT
export REGISTRY_PORT PROMETHEUS_PORT GRAFANA_PORT COLLECTOR_HEALTH_PORT COMPOSE_PROJECT_NAME=$PROJECT

detect_runtime
compose_cmd
ensure_local_dir
# Every log of this invocation goes into a directory created now, empty and its own, so no file or link that already
# exists below logs/ can be written through. It exists before any command that changes the runtime's state or
# validates the configuration (the read-only runtime detection happens earlier).
LOGS=$(mktemp -d "$STATE_DIR/logs/setup-$(date -u +%Y%m%dT%H%M%SZ).XXXXXX") || die "could not create a log directory below $STATE_DIR/logs; nothing was started"
info "Container runtime: $RUNTIME, Compose project $PROJECT"

# diagnose <reason>: what is running, the tail of every service's log, and the full logs on disk
diagnose() {
  local dir=$LOGS svc
  printf '\nSetup failed: %s\n' "$1" >&2
  {
    printf '\n--- services ---\n'
    run_bounded 30 "${COMPOSE_CMD[@]}" ps -a 2>&1 || true
    for svc in registry otel-collector prometheus grafana; do
      printf '\n--- last 25 lines of %s ---\n' "$svc"
      run_bounded 30 "${COMPOSE_CMD[@]}" logs --no-color --tail 25 "$svc" 2>&1 || true
    done
  } >&2
  for svc in registry otel-collector prometheus grafana; do
    run_bounded 60 "${COMPOSE_CMD[@]}" logs --no-color --tail 2000 "$svc" > "$dir/$svc.log" 2>&1 || true
  done
  printf '\nFull logs: %s/\n' "$dir" >&2
  printf 'Nothing was removed. Fix the cause and run ./setup.sh again, or ./teardown.sh to stop the project.\n' >&2
  exit 1
}

# fail_early_if_exited <service>: a container that has already stopped will not become ready. A runtime
# query that fails or times out is reported as that, not taken as "nothing wrong".
fail_early_if_exited() {
  local rc=0
  container_state "$1" || rc=$?
  if [ "$rc" -ne 0 ]; then
    diagnose "could not read the state of the $1 container (the runtime query $([ "$rc" -eq 124 ] && echo "timed out after ${PROBE_TIMEOUT}s" || echo "failed with status $rc"))"
  fi
  case "$Q_OUT" in
    exited* | dead*) diagnose "the $1 container stopped ($Q_OUT)" ;;
  esac
}

# wait_ready <name> <service> <seconds> <command...>: waits for the command to succeed, and fails at
# once when the container has died
wait_ready() {
  local name=$1 service=$2 secs=$3 deadline
  shift 3
  deadline=$((SECONDS + secs))
  while true; do
    if "$@" > /dev/null 2>&1; then ok "ready: $name"; return 0; fi
    fail_early_if_exited "$service"
    [ "$SECONDS" -ge "$deadline" ] && diagnose "$name was not ready after ${secs}s"
    sleep 2
  done
}

part "Checking the configuration"
OTEL_COLLECTOR_CONFIG=${OTEL_COLLECTOR_CONFIG:-./config/otel-collector.yaml}
PROMETHEUS_CONFIG=${PROMETHEUS_CONFIG:-./config/prometheus.yml}
export OTEL_COLLECTOR_CONFIG PROMETHEUS_CONFIG
for f in "$OTEL_COLLECTOR_CONFIG" "$PROMETHEUS_CONFIG" ./config/grafana/provisioning/datasources/prometheus.yml \
  ./config/grafana/provisioning/dashboards/usage.yml "$DASHBOARD_FILE"; do
  # A bind mount of a missing path would make the runtime create a directory there
  [ -f "$f" ] || die "$f is missing or not a regular file; the example's configuration is incomplete"
done
ok "configuration files are present"
DASHBOARD_UID=$(dashboard_uid) || die "$DASHBOARD_FILE is not a dashboard with a uid (not valid JSON, or no uid); nothing was started"
if ! composeq_all config -q; then
  printf '%s\n' "$BCAP_OUT" >&2
  die "docker-compose.yml does not validate with these settings (or the check timed out after ${PROBE_TIMEOUT}s)"
fi
ok "docker-compose.yml validates"

if [ -z "$registry_image_given" ]; then
  warn "REGISTRY_IMAGE is not set, so the published latest-snapshot image is used. It exports the usage metric only if it was built after the usage metrics feature merged. To test a local change, build the image (README.md, 'Build the Registry image') and set REGISTRY_IMAGE."
fi
REGISTRY_IMAGE=${REGISTRY_IMAGE:-apicurio/apicurio-registry:latest-snapshot}
export REGISTRY_IMAGE
image_rc=0
rtq image inspect --format '{{.Id}}' "$REGISTRY_IMAGE" 2> /dev/null || image_rc=$?
if [ "$image_rc" -eq 0 ]; then
  ok "Registry image $REGISTRY_IMAGE is available locally (id $(printf '%s' "$BCAP_OUT" | cut -c1-19))"
elif [ "$image_rc" -eq 124 ]; then
  die "the runtime did not answer an image query within ${PROBE_TIMEOUT}s; it may be hung. Nothing was started"
else
  info "  note  Registry image $REGISTRY_IMAGE is not local; the runtime will try to pull it"
fi

# The ports of services that are not already part of this project must be free
composeq ps --status running --services || die "could not list the running services of project $PROJECT (the query failed or timed out after ${PROBE_TIMEOUT}s); nothing was started"
running=$BCAP_OUT
port_in_use() { (exec 3<> "/dev/tcp/127.0.0.1/$1") 2> /dev/null; }
busy=""
for pair in registry:REGISTRY_PORT prometheus:PROMETHEUS_PORT grafana:GRAFANA_PORT otel-collector:COLLECTOR_HEALTH_PORT; do
  svc=${pair%%:*}
  var=${pair##*:}
  if printf '%s\n' "$running" | grep -qx "$svc"; then continue; fi
  if port_in_use "${!var}"; then busy="$busy $var=${!var}"; fi
done
if [ -n "$busy" ]; then
  die "port(s) already in use on 127.0.0.1:$busy. Choose others with the matching option (for example --registry-port 18081) or environment variable; nothing was started"
fi
ok "host ports are free (or already ours)"

part "Validating the Collector and Prometheus configuration with their own tools"
if ! run_bounded "$VALIDATE_TIMEOUT" "${COMPOSE_CMD[@]}" run --rm --no-deps -T otel-collector validate --config=/etc/otelcol/config.yaml \
  > "$LOGS/validate-collector.log" 2>&1; then
  cat "$LOGS/validate-collector.log" >&2
  die "the Collector rejected $OTEL_COLLECTOR_CONFIG (output above, also in $LOGS/validate-collector.log); nothing was started"
fi
ok "Collector configuration is valid"
if ! run_bounded "$VALIDATE_TIMEOUT" "${COMPOSE_CMD[@]}" run --rm --no-deps -T --entrypoint promtool prometheus check config /etc/prometheus/prometheus.yml \
  > "$LOGS/validate-prometheus.log" 2>&1; then
  cat "$LOGS/validate-prometheus.log" >&2
  die "promtool rejected $PROMETHEUS_CONFIG (output above, also in $LOGS/validate-prometheus.log); nothing was started"
fi
ok "Prometheus configuration is valid"

part "Starting the stack (up to ${UP_TIMEOUT}s, which allows for pulling images)"
if ! run_bounded "$UP_TIMEOUT" "${COMPOSE_CMD[@]}" up -d > "$LOGS/compose-up.log" 2>&1; then
  tail -20 "$LOGS/compose-up.log" >&2
  hint=""
  if grep -q -i -E "pull access denied|manifest unknown|not found|repository does not exist|requested access|connection refused|unauthorized" "$LOGS/compose-up.log"; then
    hint=" An image could not be pulled. If it is the Registry image ($REGISTRY_IMAGE), build it from this checkout (README.md, 'Build the Registry image') and pass it as REGISTRY_IMAGE."
  fi
  diagnose "compose up failed (output above, also in $LOGS/compose-up.log).$hint"
fi
ok "containers created"

part "Waiting for each service (bounded)"
wait_ready "Registry" registry "$REGISTRY_READY_TIMEOUT" http_get "$API/system/info"
wait_ready "Collector" otel-collector "$READY_TIMEOUT" http_get "$COLLECTOR_HEALTH_URL/"
wait_ready "Prometheus" prometheus "$READY_TIMEOUT" http_get "$PROMETHEUS_URL/-/ready"
wait_ready "Grafana" grafana "$READY_TIMEOUT" http_get "$GRAFANA_URL/api/health"

targets_up() {
  local out
  out=$(prom_query 'count(up{job=~"otel-collector-usage|otel-collector-internal"} == 1)') || return 1
  [ "$(jq -r '.data.result[0].value[1] // "0"' <<< "$out")" = 2 ]
}
wait_ready "Prometheus scraping both Collector endpoints" prometheus "$READY_TIMEOUT" targets_up

datasource_ok() {
  local out
  out=$(http_get "$GRAFANA_URL/api/datasources/uid/$GRAFANA_DATASOURCE_UID/health" -u "$GRAFANA_ADMIN_USER:$GRAFANA_ADMIN_PASSWORD") || return 1
  [ "$(jq -r .status <<< "$out")" = OK ]
}
wait_ready "Grafana's Prometheus datasource" grafana "$READY_TIMEOUT" datasource_ok
# The dashboard is part of what this example promises: wait, within the same deadline, until Grafana has loaded the
# canonical file (a missing mount or a rejected file would otherwise surface only when someone opens Grafana)
wait_ready "the dashboard $DASHBOARD_UID provisioned from the canonical file" grafana "$READY_TIMEOUT" dashboard_state "$DASHBOARD_UID"

# Old retained data is not readiness: the Prometheus volume outlives containers, so a heartbeat from a previous Registry
# process is still there when a new one starts, and a backlog can be replayed with advancing but old sample times. The
# export that counts is a sample time that is later than anything Prometheus held when this wait began AND no older than
# MAX_EXPORT_AGE_SECONDS, the limit verify.sh applies. The age compares this machine's clock with the time the Registry
# measured the sample, so the two clocks are assumed to agree to within a few seconds.
heartbeat_baseline=""
read_heartbeat_baseline() { heartbeat_baseline=$(newest_heartbeat_epoch); }
wait_ready "Prometheus answers the heartbeat query" prometheus "$READY_TIMEOUT" read_heartbeat_baseline
new_export_arrived() {
  local newest
  newest=$(newest_heartbeat_epoch) || return 1
  [ "$newest" -gt "$heartbeat_baseline" ] && [ $(($(now_epoch) - newest)) -le "$MAX_EXPORT_AGE_SECONDS" ]
}
wait_ready "a new metric export from the Registry (a heartbeat sample newer than any in Prometheus when this wait began and no older than ${MAX_EXPORT_AGE_SECONDS}s; retained older ones do not count)" registry "$READY_TIMEOUT" new_export_arrived

# The summary line only: a failed or hung query here is reported as unknown, not as a setup failure
container=""
image_line=unknown
if service_container registry; then container=$Q_OUT; fi
if [ -n "$container" ] && rtq inspect --format '{{.Config.Image}} {{.Image}}' "$container" 2> /dev/null; then image_line=$BCAP_OUT; fi

cat << EOF

The stack is running (Compose project $PROJECT). Registry image: $image_line

  Registry API   $API
  Prometheus     $PROMETHEUS_URL
  Grafana        $GRAFANA_URL   (user $GRAFANA_ADMIN_USER, password $GRAFANA_ADMIN_PASSWORD; local demonstration only)
  Collector      $COLLECTOR_HEALTH_URL/   (health only; its OTLP port is not published)
  Dashboard      $GRAFANA_URL/d/$DASHBOARD_UID   (pick the Registry process in its first filter)

EOF

if [ "$PROJECT" != usage-telemetry-otel ] || [ "$REGISTRY_PORT" != 8081 ] || [ "$PROMETHEUS_PORT" != 9090 ] \
  || [ "$GRAFANA_PORT" != 3000 ] || [ "$COLLECTOR_HEALTH_PORT" != 13133 ]; then
  cat << EOF
Next. These settings are not the defaults, so put them in the environment first (demo.sh, verify.sh
and teardown.sh read them):

  export COMPOSE_PROJECT_NAME=$PROJECT REGISTRY_PORT=$REGISTRY_PORT PROMETHEUS_PORT=$PROMETHEUS_PORT GRAFANA_PORT=$GRAFANA_PORT COLLECTOR_HEALTH_PORT=$COLLECTOR_HEALTH_PORT

EOF
else
  printf 'Next:\n\n'
fi
cat << 'EOF'
  ./demo.sh        sends real schema fetches and checks the exact counts
  ./verify.sh      re-checks the stack and the last demo run
  ./teardown.sh    stops the stack and keeps its data (--purge deletes it)
EOF
