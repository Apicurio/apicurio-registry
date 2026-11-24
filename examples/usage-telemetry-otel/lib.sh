# shellcheck shell=bash
# shellcheck disable=SC2034 # variables set here are read by the scripts that source this file
# Shared helpers for setup.sh, demo.sh, verify.sh and teardown.sh: endpoints, bounded HTTP calls,
# the container runtime, Prometheus queries and the run state. Source it; do not run it. Written for
# bash 3.2 (the macOS default) as well as bash 5.

EXAMPLE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

# Names of the Compose project and the telemetry the Registry exports
PROJECT=${COMPOSE_PROJECT_NAME:-usage-telemetry-otel}
USAGE_METRIC=apicurio_usage_schema_fetches_total
HEARTBEAT_METRIC=jvm_cpu_count
SOURCE_LABEL=service_instance_id

# Host ports (loopback only) and the URLs the scripts use. A URL variable overrides a port, which is
# how the scripts are pointed at a Registry, Prometheus or Grafana that this example did not start.
REGISTRY_PORT=${REGISTRY_PORT:-8081}
PROMETHEUS_PORT=${PROMETHEUS_PORT:-9090}
GRAFANA_PORT=${GRAFANA_PORT:-3000}
COLLECTOR_HEALTH_PORT=${COLLECTOR_HEALTH_PORT:-13133}
REGISTRY_URL=${REGISTRY_URL:-http://127.0.0.1:$REGISTRY_PORT}
API=${API:-$REGISTRY_URL/apis/registry/v3}
PROMETHEUS_URL=${PROMETHEUS_URL:-http://127.0.0.1:$PROMETHEUS_PORT}
GRAFANA_URL=${GRAFANA_URL:-http://127.0.0.1:$GRAFANA_PORT}
COLLECTOR_HEALTH_URL=${COLLECTOR_HEALTH_URL:-http://127.0.0.1:$COLLECTOR_HEALTH_PORT}
GRAFANA_ADMIN_USER='admin'
GRAFANA_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD:-admin}
# The example's own datasource; an explicit value names the Prometheus datasource of an external Grafana (--external checks)
GRAFANA_DATASOURCE_UID=${GRAFANA_DATASOURCE_UID:-apicurio-usage-prometheus}

# How old the newest Registry export may be and still count as exporting now. verify.sh, setup.sh and the lifecycle test
# all apply this one limit; the age is this machine's clock minus the sample time the Registry measured, so the clocks of
# this machine and of the Registry container are assumed to agree to within a few seconds.
MAX_EXPORT_AGE_SECONDS=${MAX_EXPORT_AGE_SECONDS:-30}

# Every HTTP call is bounded; a connection that cannot be made fails fast
HTTP_TIMEOUT=${HTTP_TIMEOUT:-10}
CONNECT_TIMEOUT=${CONNECT_TIMEOUT:-3}
# The runtime's own `info` and `compose version` probes
DETECT_TIMEOUT=${DETECT_TIMEOUT:-20}

# Where run states and failure logs go. The default is spelled physically (no symbolic link in it), so a checkout
# reached through a link is not mistaken for a linked state path. Only a directory this example created (it holds
# the marker file) is ever written to, and a LOCAL_DIR that passes through a symbolic link is refused (see
# check_local_path).
LOCAL_DIR=${LOCAL_DIR:-$(cd "$EXAMPLE_DIR" && pwd -P)/.local}
LOCAL_MARKER=.usage-telemetry-otel-owned

failures=0

ok()   { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
part() { printf '\n%s\n' "$1"; }
info() { printf '%s\n' "$*"; }
warn() { printf 'warning: %s\n' "$*" >&2; }
die()  { printf 'error: %s\n' "$*" >&2; exit 1; }

need_commands() {
  local c
  for c in "$@"; do
    command -v "$c" > /dev/null 2>&1 || die "'$c' is required but was not found in PATH"
  done
}

# check <description> <expected> <actual>
check() {
  if [[ "$3" == "$2" ]]; then ok "$1"; else fail "$1: got '$3', expected '$2'"; fi
}

now_epoch() { date +%s; }
iso_from_epoch() { date -u -r "$1" +%Y-%m-%dT%H:%M:%SZ 2> /dev/null || date -u -d "@$1" +%Y-%m-%dT%H:%M:%SZ; }

# http_status <curl args...>: the HTTP status, or 000 when no response arrived in time
http_status() {
  curl -s --max-time "$HTTP_TIMEOUT" --connect-timeout "$CONNECT_TIMEOUT" -o /dev/null -w '%{http_code}' "$@" || true
}

# http_get <url> [curl args...]: the body; non-zero on a connection failure or an HTTP error status
http_get() {
  local url=$1
  shift
  curl -sS --fail --max-time "$HTTP_TIMEOUT" --connect-timeout "$CONNECT_TIMEOUT" "$@" "$url"
}

# api_post <path> <json>: POST to the Registry API with the shared connect and total timeouts. The response body is
# written to stdout; the status is non-zero, with the reason on stderr, on a connection or HTTP error. curl's own
# timeouts bound it; it is not a process-group supervised command.
api_post() {
  local body
  if ! body=$(curl -sS --fail-with-body --max-time 30 --connect-timeout "$CONNECT_TIMEOUT" -X POST "$API$1" \
    -H 'Content-Type: application/json' -d "$2" 2>&1); then
    printf 'POST %s failed: %s\n' "$1" "$body" >&2
    return 1
  fi
  printf '%s' "$body"
}

# retry_until <seconds> <command...>: runs the command every second until it succeeds or the
# deadline passes. Returns the command's last status.
retry_until() {
  local deadline=$((SECONDS + $1)) rc=1
  shift
  while true; do
    if "$@"; then return 0; else rc=$?; fi
    [ "$SECONDS" -ge "$deadline" ] && return "$rc"
    sleep 1
  done
}

# _group_members <pgid>: the live (not zombie) processes of one process group
_group_members() { ps -A -o pid=,pgid=,stat= 2> /dev/null | awk -v g="$1" '$2 == g && $3 !~ /^Z/ { print $1 }'; }

# _supervision_possible: whether the process table can be read the way run_bounded needs
_supervision_possible() {
  ps -A -o pid=,pgid=,stat= 2> /dev/null | awk '$1 ~ /^[0-9]+$/ && $2 ~ /^[0-9]+$/ && $3 != "" { found = 1 } END { exit !found }'
}

# _terminate_group <pid>: ends the process group run_bounded created for <pid> (job control makes the group id the
# leader's pid, so the pid is the only identity kept), and nothing else. TERM first, KILL after three seconds, then reaps
# the leader. Both signals go to the group id only, and only while the group still has live members. That avoids
# signalling a group that is already empty; it does not prove the id was not recycled between the check and the signal,
# and no such guarantee is claimed.
_terminate_group() {
  local pid=$1 i=0
  {
    if [ -n "$(_group_members "$pid")" ]; then
      kill -TERM -- "-$pid" 2> /dev/null || true
      while [ -n "$(_group_members "$pid")" ] && [ "$i" -lt 15 ]; do sleep 0.2; i=$((i + 1)); done
      [ -z "$(_group_members "$pid")" ] || kill -KILL -- "-$pid" 2> /dev/null || true
    fi
    wait "$pid"
  } 2> /dev/null || true
}

_RB_PID=""
_RB_TMP=""
_RB_BEFORE=""
_RB_SAVED_INT=""
_RB_SAVED_TERM=""
# A command a caller wants run when a signal ends run_bounded, just before it exits (the lifecycle test uses it to release
# its claim while it is cleaning up). Empty by default.
RB_EXIT_HOOK=""

# _last_background_pid: sets _LBP to $! (empty when this shell has started no background job). bash 3.2 treats an unset $!
# as an unbound variable under `set -u`, so the expansion is made with nounset off.
_last_background_pid() {
  case $- in
    *u*)
      set +u
      _LBP=$!
      set -u
      ;;
    *) _LBP=$! ;;
  esac
}

# _rb_restore_traps: puts back the INT and TERM traps the caller had when run_bounded began
_rb_restore_traps() {
  eval "${_RB_SAVED_INT:-trap - INT}"
  eval "${_RB_SAVED_TERM:-trap - TERM}"
  _RB_PID=""
}

# Called on INT or TERM from the moment run_bounded begins to launch its command until it returns: ends the owned
# invocation, removes its capture file, then leaves with the usual status. The owned job is identified by one value,
# _RB_PID, so no signal can find it half recorded. A signal can still be handled between the launch of the command and
# the assignment of _RB_PID; the pid is then taken from $!. One that arrives before the launch finds nothing to end.
_rb_signal() {
  local pid=$_RB_PID
  if [ -z "$pid" ]; then
    _last_background_pid
    if [ -n "$_LBP" ] && [ "$_LBP" != "$_RB_BEFORE" ]; then pid=$_LBP; fi
  fi
  [ -z "$pid" ] || _terminate_group "$pid"
  [ -z "$_RB_TMP" ] || rm -f "$_RB_TMP"
  [ -z "$RB_EXIT_HOOK" ] || "$RB_EXIT_HOOK" || true
  exit "$1"
}

# run_bounded <seconds> <command...>: runs the command as its own process group (job control gives the
# group the leader's pid), so a deadline, INT or TERM ends the command and every process it started,
# such as the provider behind `podman compose`. The deadline covers the whole group: the invocation ends
# when the leader AND every other member of its group have ended, so a child that outlives the leader
# (and may hold its output pipe open) is waited for, then stopped at the deadline. Only that group is
# signalled: never the caller's group, never a pattern match. Status: the leader's own; 124 on a deadline;
# 130 or 143 (after cleanup) on INT or TERM; 125, without starting the command, when the group cannot
# be supervised (no readable process table, no job control). The INT and TERM handlers are installed before the
# command is launched, so a signal that arrives while its pid is being recorded still ends it. A command that
# deliberately starts a new session or group of its own is out of reach.
run_bounded() {
  local limit=$1 pid own now rc=0 end had_m=false
  shift
  if [ "${BASH_SUBSHELL:-0}" -ne 0 ]; then
    # A signal sent to the entry-point script is only acted on by that shell. In a command substitution, a pipeline
    # or any other subshell, the supervising trap would be deferred until the command ended on its own, so the
    # owned command would outlive the script. Use bcap (or rtq, composeq) in the entry-point shell instead.
    printf 'error: not starting "%s": run_bounded must be called from the entry-point shell, not from a command substitution, pipeline or subshell (use bcap, rtq or composeq, which capture output without one)\n' "$1" >&2
    return 125
  fi
  if ! _supervision_possible; then
    printf 'error: not starting "%s": its process group cannot be supervised (ps -A -o pid=,pgid=,stat= is not usable), so a timeout could not be enforced\n' "$1" >&2
    return 125
  fi
  case $- in *m*) had_m=true ;; esac
  set -m
  case $- in
    *m*) ;;
    *)
      printf 'error: not starting "%s": bash job control is unavailable, so its process group cannot be supervised\n' "$1" >&2
      return 125
      ;;
  esac
  _RB_SAVED_INT=$(trap -p INT)
  _RB_SAVED_TERM=$(trap -p TERM)
  _RB_PID=""
  _last_background_pid
  _RB_BEFORE=$_LBP
  trap '_rb_signal 130' INT
  trap '_rb_signal 143' TERM
  "$@" < /dev/null &
  pid=$!
  _RB_PID=$pid
  $had_m || set +m
  # While the leader is still running, confirm it leads a group of its own that is not the caller's
  if kill -0 "$pid" 2> /dev/null; then
    now=$(ps -o pgid= -p "$pid" 2> /dev/null | tr -d ' ')
    own=$(ps -o pgid= -p "$$" 2> /dev/null | tr -d ' ')
    if [ -n "$now" ] && { [ "$now" != "$pid" ] || [ "$now" = "$own" ]; }; then
      kill -TERM "$pid" 2> /dev/null || true
      wait "$pid" 2> /dev/null || true
      printf 'error: "%s" did not get a process group of its own; stopped it rather than run it with half a timeout\n' "$1" >&2
      _rb_restore_traps
      return 125
    fi
  fi
  end=$((SECONDS + limit))
  while :; do
    if ! kill -0 "$pid" 2> /dev/null && [ -z "$(_group_members "$pid")" ]; then break; fi
    if [ "$SECONDS" -ge "$end" ]; then
      _terminate_group "$pid"
      _rb_restore_traps
      return 124
    fi
    sleep 0.2
  done
  wait "$pid" 2> /dev/null || rc=$?
  _rb_restore_traps
  return "$rc"
}

# --- supervised commands whose output is needed ----------------------------------------------------------------
#
# Output is captured in the entry-point shell, in a temporary file this shell owns, never through a command
# substitution: the shell that receives INT or TERM must be the shell that supervises the command (see run_bounded).
# A status and an output are separate things: the status is returned (124 when the deadline passed), the output is in
# BCAP_OUT (trailing newlines stripped, like $(...)). A caller checks the status before it looks at the output, and a
# failed or timed-out query is unknown, never an empty answer.

# _bcap <yes|no: merge stderr into the output> <seconds> <command...>
_bcap() {
  local merge=$1 limit=$2 rc=0
  shift 2
  BCAP_OUT=""
  _RB_TMP=$(mktemp "${TMPDIR:-/tmp}/usage-telemetry-otel-capture.XXXXXX") || return 125
  if [ "$merge" = yes ]; then
    run_bounded "$limit" "$@" > "$_RB_TMP" 2>&1 || rc=$?
  else
    run_bounded "$limit" "$@" > "$_RB_TMP" || rc=$?
  fi
  BCAP_OUT=$(cat "$_RB_TMP")
  rm -f "$_RB_TMP"
  _RB_TMP=""
  return "$rc"
}
# bcap <seconds> <command...>: supervised, stdout captured (stderr passes through); sets BCAP_OUT, returns the status
bcap() { _bcap no "$@"; }
# bcap_all <seconds> <command...>: the same with stderr captured too
bcap_all() { _bcap yes "$@"; }

# rt <runtime args...>: one bounded runtime command whose output goes to the caller's stdout (never call it inside
# $(...) or a pipeline: run_bounded refuses). The status is the command's, or 124 when it timed out.
PROBE_TIMEOUT=${PROBE_TIMEOUT:-30}
rt() { run_bounded "$PROBE_TIMEOUT" "$RUNTIME" "$@"; }
# rtq <runtime args...>: a bounded runtime query with its output in BCAP_OUT
rtq() { bcap "$PROBE_TIMEOUT" "$RUNTIME" "$@"; }

# syntax_check_each <files...>: bash -n on every file in its own invocation (one `bash -n a b` parses only
# a). Prints each file that does not parse; the status is the number of such files.
syntax_check_each() {
  local f bad=0
  for f in "$@"; do
    if ! bash -n "$f" 2> /dev/null; then
      printf 'syntax error: %s\n' "$f"
      bad=$((bad + 1))
    fi
  done
  return "$bad"
}

# --- local output directory -------------------------------------------------------------------------

# validate_project: the Compose project name is part of file system paths and of container names, so only
# a conservative character set is accepted
validate_project() {
  case "$PROJECT" in
    *[!a-z0-9_-]* | "" | -* | _*) die "the project name must start with a lowercase letter or digit and use only lowercase letters, digits, - and _, got '$PROJECT'" ;;
  esac
}

# state_dir: this project's own output directory (run states, failure logs) below LOCAL_DIR. Projects never
# share a state directory, so one project's purge or run cannot touch another's.
state_dir() { printf '%s/projects/%s' "$LOCAL_DIR" "$PROJECT"; }

# check_local_path: refuses a LOCAL_DIR that passes through a symbolic link, whatever its spelling (a trailing slash,
# repeated separators, "/." components, a linked component below an ordinary base). Every component of the
# path is checked, not the raw string. The only links allowed are those directly under / (the macOS aliases /tmp,
# /var, /etc), so ordinary temporary directories keep working; a ".." component is refused because its target cannot be
# told from its spelling. On success LOCAL_DIR is the normalized path, which the callers then use. It creates nothing.
check_local_path() {
  local p=$LOCAL_DIR part prefix="" resolved
  local parts=()
  case "$p" in /*) ;; *) p=$(pwd -P)/$p ;; esac
  IFS=/ read -r -a parts <<< "$p"
  for part in "${parts[@]}"; do
    case "$part" in
      "" | .) continue ;;
      ..) die "LOCAL_DIR=$LOCAL_DIR contains a '..' component, whose target cannot be told from its spelling; give the physical path. Nothing was changed" ;;
    esac
    prefix="$prefix/$part"
    if [ -L "$prefix" ]; then
      case "$prefix" in
        /*/*)
          resolved=$(cd "$prefix" 2> /dev/null && pwd -P) || resolved=""
          die "LOCAL_DIR=$LOCAL_DIR passes through the symbolic link $prefix${resolved:+ (to $resolved)}; give the physical path instead. Nothing was changed"
          ;;
      esac
    fi
  done
  LOCAL_DIR=${prefix:-/}
}

# ensure_local_dir: creates the output directory of the current project and sets STATE_DIR. Refuses a
# LOCAL_DIR that this example did not create, and any of its directories that is a symbolic link.
ensure_local_dir() {
  validate_project
  check_local_path
  if [ -L "$LOCAL_DIR" ]; then die "LOCAL_DIR=$LOCAL_DIR is a symbolic link; refusing to write through it"; fi
  if [ -e "$LOCAL_DIR" ] && [ ! -d "$LOCAL_DIR" ]; then
    die "LOCAL_DIR=$LOCAL_DIR exists and is not a directory"
  fi
  if [ -d "$LOCAL_DIR" ] && [ ! -f "$LOCAL_DIR/$LOCAL_MARKER" ]; then
    if [ -n "$(ls -A "$LOCAL_DIR" 2> /dev/null)" ]; then
      die "LOCAL_DIR=$LOCAL_DIR is not empty and was not created by this example (no $LOCAL_MARKER file in it); refusing to write there"
    fi
  fi
  mkdir -p "$LOCAL_DIR"
  [ ! -L "$LOCAL_DIR/$LOCAL_MARKER" ] || die "$LOCAL_DIR/$LOCAL_MARKER is a symbolic link; refusing to continue"
  [ -f "$LOCAL_DIR/$LOCAL_MARKER" ] || printf 'Output of the usage-telemetry-otel example (run states and failure logs, one directory per Compose project under projects/). Safe to delete.\n' > "$LOCAL_DIR/$LOCAL_MARKER"
  STATE_DIR=$(state_dir)
  local d
  for d in "$LOCAL_DIR/projects" "$STATE_DIR" "$STATE_DIR/runs" "$STATE_DIR/logs"; do
    [ ! -L "$d" ] || die "$d is a symbolic link; refusing to write through it"
    mkdir -p "$d"
  done
}

# state_purgeable: for a destructive action on this project's saved state. Writes and creates nothing.
# Returns 0 when there is state this example owns that may be removed, 1 when there is none (no LOCAL_DIR,
# no marker, no project directory), and dies, before the caller has removed anything, when LOCAL_DIR passes through a
# symbolic link in any spelling (check_local_path), or when the marker, projects/, the project directory or its
# runs/ is a symbolic link.
state_purgeable() {
  local d
  validate_project
  check_local_path
  [ -e "$LOCAL_DIR" ] || [ -L "$LOCAL_DIR" ] || return 1
  [ ! -L "$LOCAL_DIR" ] || die "refusing to purge: LOCAL_DIR=$LOCAL_DIR is a symbolic link; nothing was removed"
  [ -d "$LOCAL_DIR" ] || die "refusing to purge: LOCAL_DIR=$LOCAL_DIR is not a directory; nothing was removed"
  [ ! -L "$LOCAL_DIR/$LOCAL_MARKER" ] || die "refusing to purge: $LOCAL_DIR/$LOCAL_MARKER is a symbolic link; nothing was removed"
  [ -f "$LOCAL_DIR/$LOCAL_MARKER" ] || return 1
  for d in "$LOCAL_DIR/projects" "$(state_dir)" "$(state_dir)/runs"; do
    [ ! -L "$d" ] || die "refusing to purge: $d is a symbolic link; nothing was removed"
  done
  [ -d "$(state_dir)" ]
}

# write_atomically <destination> < content: a reader sees the old file or the new one, never a partial one. The
# destination must be absent or an ordinary file that is not a symbolic link. A directory, a link to one, a broken
# link or anything else is refused before a byte is written, because mv would put the file inside the directory or
# follow the link. The check is made just before the rename; it is not a lock against a concurrent writer.
write_atomically() {
  local dest=$1 tmp
  if [ -L "$dest" ] || { [ -e "$dest" ] && [ ! -f "$dest" ]; }; then
    printf 'error: not writing %s: it exists and is not an ordinary file (a directory, a symbolic link or something else); nothing was changed\n' "$dest" >&2
    return 1
  fi
  tmp=$(mktemp "$(dirname "$dest")/.tmp.XXXXXX") || return 1
  if cat > "$tmp" && chmod 644 "$tmp" && mv -f "$tmp" "$dest"; then return 0; fi
  rm -f "$tmp"
  return 1
}

# --- container runtime -------------------------------------------------------------------------------

# Podman is preferred; CONTAINER_RUNTIME=docker (or podman) chooses explicitly.
# Sets RUNTIME and COMPOSE (an array); dies with the fix when none works.
detect_runtime() {
  local requested=${CONTAINER_RUNTIME:-}
  if [ -z "$requested" ]; then
    if command -v podman > /dev/null 2>&1; then requested=podman
    elif command -v docker > /dev/null 2>&1; then requested=docker
    else die "neither podman nor docker was found in PATH. Install Podman (preferred) or Docker, with Compose v2; to force one set CONTAINER_RUNTIME=podman or CONTAINER_RUNTIME=docker"
    fi
  fi
  case "$requested" in
    podman | docker) ;;
    *) die "CONTAINER_RUNTIME must be podman or docker, got '$requested'" ;;
  esac
  command -v "$requested" > /dev/null 2>&1 || die "CONTAINER_RUNTIME=$requested, but '$requested' was not found in PATH"
  RUNTIME=$requested
  # podman prints a banner on every compose call to say which provider it runs
  export PODMAN_COMPOSE_WARNING_LOGS=false
  COMPOSE=("$RUNTIME" compose)

  if ! run_bounded "$DETECT_TIMEOUT" "$RUNTIME" info > /dev/null 2>&1; then
    if [ "$RUNTIME" = podman ]; then
      die "podman is installed but not usable. On macOS or Windows start the machine first: 'podman machine start' (this script never starts it for you); then retry"
    fi
    die "docker is installed but the daemon is not reachable; start Docker and retry"
  fi
  local version vrc=0
  bcap_all "$DETECT_TIMEOUT" "${COMPOSE[@]}" version || vrc=$?
  version=$BCAP_OUT
  if [ "$vrc" -ne 0 ]; then
    die "could not determine the Compose version: '$RUNTIME compose version' $([ "$vrc" -eq 124 ] && echo "timed out after ${DETECT_TIMEOUT}s" || echo "failed with status $vrc") (it said: $(printf '%s' "$version" | tr '\n' ' ' | cut -c1-200)). A Compose v2 provider that does not run cannot be relied on; fix or reinstall it and retry"
  fi
  case "$version" in
    *podman-compose*) die "podman is using podman-compose, which this example does not support. Install Compose v2 (docker-compose v2 or the docker compose plugin) and retry" ;;
    *[Vv]ersion\ v2.* | *[Vv]ersion\ 2.* | *[Vv]ersion\ v[3-9]* | *[Vv]ersion\ [3-9]*) ;;
    *) die "Compose v2 or later is required ('$RUNTIME compose version' said: $(printf '%s' "$version" | tr '\n' ' ' | cut -c1-200))" ;;
  esac
}

# compose_cmd: the Compose CLI for this example's file and project, as one command (so run_bounded
# ends the Compose process itself rather than a shell around it)
compose_cmd() {
  COMPOSE_CMD=("${COMPOSE[@]}" -f "$EXAMPLE_DIR/docker-compose.yml" --project-directory "$EXAMPLE_DIR" -p "$PROJECT")
}

# compose <args...>: one bounded Compose command for this example's file and project (PROBE_TIMEOUT seconds), its
# output going to the caller's stdout (never inside $(...) or a pipeline). Long operations (up, run, stop, start,
# restart, down, logs) call run_bounded with "${COMPOSE_CMD[@]}" and their own deadline instead.
compose() {
  compose_cmd
  run_bounded "$PROBE_TIMEOUT" "${COMPOSE_CMD[@]}" "$@"
}
# composeq <args...>: a bounded Compose query with its output in BCAP_OUT; composeq_all also captures stderr
composeq() {
  compose_cmd
  bcap "$PROBE_TIMEOUT" "${COMPOSE_CMD[@]}" "$@"
}
composeq_all() {
  compose_cmd
  bcap_all "$PROBE_TIMEOUT" "${COMPOSE_CMD[@]}" "$@"
}

# The inventory queries below run in the entry-point shell. Each sets Q_OUT to its answer and returns the query's
# status (124 when it timed out): Q_OUT is meaningful only when the status is 0. Only published_port_count also
# sets Q_ERR, to say what failed; the others leave it empty and the status is all they report.
Q_OUT=""
Q_ERR=""

# service_container <service>: Q_OUT is the container ID of a service (empty when it has none)
service_container() {
  Q_OUT=""
  composeq ps -a -q "$1" || return $?
  Q_OUT=$(printf '%s\n' "$BCAP_OUT" | head -1)
}

# container_state <service>: Q_OUT is "<status> exit code <n>" of the service's container, empty when there is none
container_state() {
  local id
  service_container "$1" || return $?
  id=$Q_OUT
  Q_OUT=""
  [ -n "$id" ] || return 0
  rtq inspect --format '{{.State.Status}} exit code {{.State.ExitCode}}' "$id" || return $?
  Q_OUT=$BCAP_OUT
}

# count_project_containers: Q_OUT is how many containers of this project exist, running or not
count_project_containers() {
  Q_OUT=""
  composeq ps -a -q || return $?
  Q_OUT=$(printf '%s\n' "$BCAP_OUT" | awk 'NF' | wc -l | tr -d ' ')
}

# project_volumes: Q_OUT is the names of this project's volumes, one per line
project_volumes() {
  Q_OUT=""
  rtq volume ls -q --filter "label=com.docker.compose.project=$PROJECT" || return $?
  Q_OUT=$BCAP_OUT
}

# project_volume_count: Q_OUT is how many volumes this project has
project_volume_count() {
  project_volumes || return $?
  Q_OUT=$(printf '%s\n' "$Q_OUT" | awk 'NF' | wc -l | tr -d ' ')
}

# project_networks: Q_OUT is the names of this project's networks (by the Compose project label), one per line
project_networks() {
  Q_OUT=""
  rtq network ls -q --filter "label=com.docker.compose.project=$PROJECT" || return $?
  Q_OUT=$BCAP_OUT
}

# other_containers: Q_OUT is "<id> <state>" of every container that is not this project's, sorted. No other container
# at all is a valid, empty answer; a failed or timed-out query is a failure.
other_containers() {
  local all
  Q_OUT=""
  rtq ps -a --format '{{.ID}} {{.State}}' || return $?
  all=$BCAP_OUT
  composeq ps -a -q || return $?
  Q_OUT=$(printf '%s\n' "$all" | MINE="$BCAP_OUT" awk '
    BEGIN { n = split(ENVIRON["MINE"], ids, "\n"); for (i = 1; i <= n; i++) if (ids[i] != "") skip[substr(ids[i], 1, 12)] = 1 }
    NF && !(substr($1, 1, 12) in skip)' | LC_ALL=C sort)
}

# published_port_count <service> <container port>: Q_OUT is how many mappings publish that container port on the host.
# The container lookup and the port query are both checked: an unknown is a failure (Q_ERR says which), never a zero.
published_port_count() {
  local id rc=0
  Q_OUT=""
  Q_ERR=""
  service_container "$1" || {
    rc=$?
    Q_ERR="could not find the container of service $1: the container lookup $([ "$rc" -eq 124 ] && echo "timed out" || echo "failed (status $rc)")"
    return 1
  }
  id=$Q_OUT
  Q_OUT=""
  if [ -z "$id" ]; then
    Q_ERR="could not find the container of service $1: it has none"
    return 1
  fi
  rtq port "$id" || {
    rc=$?
    Q_ERR="the port query $([ "$rc" -eq 124 ] && echo "timed out after ${PROBE_TIMEOUT}s" || echo "failed (status $rc)") for the container of service $1"
    return 1
  }
  Q_OUT=$(printf '%s\n' "$BCAP_OUT" | awk -v p="$2" '$1 == p "/tcp" || $1 == p "/udp" { n++ } END { print n + 0 }')
}

# --- Prometheus and Grafana ------------------------------------------------------------------------

# prom_query <promql> [evaluation time, epoch seconds]: the JSON answer. Non-zero when Prometheus does
# not answer or answers with an error; callers treat that as "unknown", never as "no data".
prom_query() {
  local out extra=()
  [ -n "${2:-}" ] && extra=(--data-urlencode "time=$2")
  out=$(curl -sS --fail --max-time "$HTTP_TIMEOUT" --connect-timeout "$CONNECT_TIMEOUT" -G \
    --data-urlencode "query=$1" ${extra[@]+"${extra[@]}"} "$PROMETHEUS_URL/api/v1/query") || return 1
  jq -e '.status == "success"' > /dev/null <<< "$out" || return 1
  printf '%s' "$out"
}

# grafana_query <promql> [time]: the same query through Grafana's provisioned datasource
grafana_query() {
  local out extra=()
  [ -n "${2:-}" ] && extra=(--data-urlencode "time=$2")
  out=$(curl -sS --fail --max-time "$HTTP_TIMEOUT" --connect-timeout "$CONNECT_TIMEOUT" -G \
    -u "$GRAFANA_ADMIN_USER:$GRAFANA_ADMIN_PASSWORD" \
    --data-urlencode "query=$1" ${extra[@]+"${extra[@]}"} \
    "$GRAFANA_URL/api/datasources/proxy/uid/$GRAFANA_DATASOURCE_UID/api/v1/query") || return 1
  jq -e '.status == "success"' > /dev/null <<< "$out" || return 1
  printf '%s' "$out"
}

# The one canonical dashboard (distro/metrics/usage), which docker-compose.yml mounts into Grafana. The UID comes from
# the file itself, so the scripts never carry a second copy of it.
DASHBOARD_FILE=${DASHBOARD_FILE:-$EXAMPLE_DIR/../../distro/metrics/usage/grafana/schema-usage.json}

# dashboard_uid [file]: the UID of the dashboard file. Non-zero, printing nothing, when the file has none.
dashboard_uid() {
  jq -er '.uid | select(type == "string" and length > 0)' "${1:-$DASHBOARD_FILE}" 2> /dev/null
}

# dashboard_state <uid>: sets DASHBOARD_STATE to "provisioned" (Grafana holds it, loaded from a file), "not-provisioned"
# (Grafana holds a dashboard with that UID that no file provisioned), "missing" (404) or "unknown" (Grafana did not answer,
# or answered with an error or a body that is not a dashboard). Succeeds only for "provisioned".
dashboard_state() {
  local out code body
  DASHBOARD_STATE=unknown
  out=$(curl -sS --max-time "$HTTP_TIMEOUT" --connect-timeout "$CONNECT_TIMEOUT" -u "$GRAFANA_ADMIN_USER:$GRAFANA_ADMIN_PASSWORD" \
    -w '\n%{http_code}' "$GRAFANA_URL/api/dashboards/uid/$1" 2> /dev/null) || return 1
  code=${out##*$'\n'}
  body=${out%$'\n'*}
  case "$code" in
    404) DASHBOARD_STATE=missing; return 1 ;;
    200) ;;
    *) return 1 ;;
  esac
  jq -e --arg uid "$1" '.dashboard.uid == $uid and (.meta | type == "object")' > /dev/null 2>&1 <<< "$body" || return 1
  if [ "$(jq -r '.meta.provisioned // false' <<< "$body")" = true ]; then
    DASHBOARD_STATE=provisioned
    return 0
  fi
  DASHBOARD_STATE=not-provisioned
  return 1
}

# identifier_pairs: reads a run state, or a JSON object with the same schemas and notCounted lists, and prints every
# identifier of the run as "<identifier type>:<value>", sorted and unique: the global and content ID of each schema, and
# the ID of each request that must not be counted (its type comes from the request path, /ids/globalIds or /ids/contentIds).
# A global ID and a content ID with the same number are two different identifiers, so they stay two pairs.
identifier_pairs() {
  jq -r '[(.schemas[] | ("global_id:\(.globalId)", "content_id:\(.contentId)")),
          (.notCounted[] | .request | capture("^GET /ids/(?<kind>globalIds|contentIds)/(?<id>[0-9]+)")
            | ((if .kind == "contentIds" then "content_id" else "global_id" end) + ":" + .id))] | unique | .[]'
}

# usage_selector_for_run <instance>: reads a run state (or a JSON object with its schemas and notCounted lists) on stdin
# and prints the selector of every series of that Registry process that carries one of the run's identifiers, as typed
# (type, value) pairs. demo.sh and verify.sh both use it, so they select the same series. Non-zero, printing nothing, when
# the input yields no identifier.
usage_selector_for_run() {
  local instance=$1 pair pairs=()
  while IFS= read -r pair; do pairs+=("$pair"); done < <(identifier_pairs)
  usage_selector "$instance" ${pairs[@]+"${pairs[@]}"}
}

# usage_selector <instance> <type:value ...>: every usage series of one Registry process that carries one of the
# identifiers, matched as (identifier type, value) pairs: one selector per type, joined with `or`. Global and content
# IDs come from independent sequences, so a bare value that is a content ID in one series and a global ID in another
# must not select both. Returns 2, printing nothing, for a pair whose type is unknown or whose value is not a
# number (the values end up in a regular expression).
usage_selector() {
  local instance=$1 pair type value globals="" contents="" selector=""
  shift
  for pair in "$@"; do
    type=${pair%%:*}
    value=${pair#*:}
    case "$value" in *[!0-9]* | "") printf 'error: identifier %s is not <type>:<number>\n' "$pair" >&2; return 2 ;; esac
    case "$type" in
      global_id) globals="$globals|$value" ;;
      content_id) contents="$contents|$value" ;;
      *) printf 'error: identifier %s has an unknown type\n' "$pair" >&2; return 2 ;;
    esac
  done
  [ -z "$globals" ] || selector=$(printf '%s{%s="%s",apicurio_registry_identifier_type="global_id",apicurio_registry_identifier_value=~"%s"}' "$USAGE_METRIC" "$SOURCE_LABEL" "$instance" "${globals#|}")
  if [ -n "$contents" ]; then
    selector="${selector:+$selector or }$(printf '%s{%s="%s",apicurio_registry_identifier_type="content_id",apicurio_registry_identifier_value=~"%s"}' "$USAGE_METRIC" "$SOURCE_LABEL" "$instance" "${contents#|}")"
  fi
  [ -n "$selector" ] || return 2
  printf '%s' "$selector"
}

# series_tsv: reads a Prometheus answer, prints sorted "client<TAB>type<TAB>value<TAB>operation<TAB>count"
series_tsv() {
  jq -r '.data.result[] | [.metric.apicurio_registry_client_id, .metric.apicurio_registry_identifier_type,
         .metric.apicurio_registry_identifier_value, .metric.apicurio_registry_operation, .value[1]] | @tsv' | LC_ALL=C sort
}

# heartbeat_epoch <instance>: seconds since the epoch of the newest sample the Collector passed on for
# that Registry process. Not the scrape time: the Collector forwards the time the Registry measured.
heartbeat_epoch() {
  local out
  out=$(prom_query "timestamp($HEARTBEAT_METRIC{$SOURCE_LABEL=\"$1\"})") || return 1
  jq -er '.data.result[0].value[1] // empty | tonumber | floor' <<< "$out"
}

# newest_heartbeat: "instance epoch" of the Registry process with the newest heartbeat sample
newest_heartbeat() {
  local out
  out=$(prom_query "timestamp($HEARTBEAT_METRIC)") || return 1
  jq -er '[.data.result[] | {i: .metric.service_instance_id, t: (.value[1] | tonumber | floor)}] | sort_by(.t) | last | select(. != null) | "\(.i) \(.t)"' <<< "$out"
}

# newest_heartbeat_epoch: the newest heartbeat sample time of any Registry process, 0 when Prometheus holds none.
# Non-zero when Prometheus does not answer, which is unknown and not the same as 0.
newest_heartbeat_epoch() {
  local out
  out=$(prom_query "timestamp($HEARTBEAT_METRIC)") || return 1
  jq -er '[.data.result[] | (.value[1] | tonumber | floor)] | (max // 0)' <<< "$out"
}
