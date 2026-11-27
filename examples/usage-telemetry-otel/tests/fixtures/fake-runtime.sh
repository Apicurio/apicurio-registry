#!/usr/bin/env bash
# A stand-in for `podman` or `docker`, for tests/unit-test.sh only. It answers the few commands the scripts
# send and does nothing else, so those tests never touch a real daemon or a real container.
# Behavior is set by environment variables:
#   FAKE_LOG            file that gets one line per invocation (the arguments)
#   FAKE_STALL          comma list of commands that hang (ps, compose-ps, compose-ps-running, compose-version, volume,
#                       network, inspect, port, down): compose-ps-running is `compose ps --status ...` only; each starts a
#                       provider process, like the one behind `podman compose`, that starts a `sleep 300` grandchild
#                       and waits for it; the pids of both are appended to FAKE_PIDS
#   FAKE_FLAG_DIR       a directory of switches that can change while a test runs: a file named ps, compose-ps,
#                       compose-ps-running, compose-version, volume, network, port, inspect or down makes that command
#                       fail with the status the file holds, or hang if it holds "stall"
#   FAKE_VERSION_OUT / FAKE_VERSION_RC  output (default a Docker Compose v2 banner) and status of `compose version`
#   FAKE_NETWORKS       output of `network ls`
#   FAKE_STUBBORN       when set, a hanging provider ignores TERM (only KILL ends it)
#   FAKE_PS_OUT / FAKE_PS_RC            output and status of `ps -a --format ...`
#   FAKE_COMPOSE_PS_OUT / FAKE_COMPOSE_PS_RC   the same for `compose ps`; a file compose-ps-out in FAKE_FLAG_DIR, when
#                       it exists, supplies the output instead (so it can appear while a test runs)
#   FAKE_VOLUMES        output of `volume ls`
#   FAKE_PORT_OUT / FAKE_PORT_RC        output and status of `port <container>`
#   FAKE_DOWN_RC        status of `compose down` (default 0)
#   FAKE_CONFIG_RC      status of `compose config` (default 0)
#   FAKE_INSPECT        output of `inspect` (default "running exit code 0")
set -u

printf '%s\n' "$(basename "$0") $*" >> "${FAKE_LOG:-/dev/null}"

# hang_now: a provider process (a child of this one) that starts a process which never ends within a test
hang_now() {
  # a provider that ignores TERM, so that only KILL ends it (the processes it starts inherit that)
  [ -z "${FAKE_STUBBORN:-}" ] || trap '' TERM
  (
    sleep 300 &
    printf '%s\n' "$!" >> "${FAKE_PIDS:-/dev/null}"
    wait
  ) &
  printf '%s\n' "$!" >> "${FAKE_PIDS:-/dev/null}"
  wait
}

stall() {
  case ",${FAKE_STALL:-}," in
    *",$1,"*) hang_now ;;
  esac
}

# flagged <name>: honors a switch file in FAKE_FLAG_DIR
flagged() {
  local flag
  [ -n "${FAKE_FLAG_DIR:-}" ] && [ -f "$FAKE_FLAG_DIR/$1" ] || return 0
  flag=$(cat "$FAKE_FLAG_DIR/$1")
  if [ "$flag" = stall ]; then
    hang_now
    exit 124
  fi
  exit "$flag"
}

case "${1:-}" in
  info) exit "${FAKE_INFO_RC:-0}" ;;
  ps)
    stall ps
    flagged ps
    [ -z "${FAKE_PS_RC:-}" ] || exit "$FAKE_PS_RC"
    printf '%s' "${FAKE_PS_OUT:-}"
    ;;
  volume)
    stall volume
    flagged volume
    printf '%s' "${FAKE_VOLUMES:-}"
    ;;
  network)
    stall network
    flagged network
    printf '%s' "${FAKE_NETWORKS:-}"
    ;;
  inspect)
    stall inspect
    flagged inspect
    printf '%s\n' "${FAKE_INSPECT:-running exit code 0}"
    ;;
  port)
    stall port
    flagged port
    [ -z "${FAKE_PORT_RC:-}" ] || exit "$FAKE_PORT_RC"
    printf '%s' "${FAKE_PORT_OUT:-}"
    ;;
  image) exit "${FAKE_IMAGE_RC:-1}" ;;
  compose)
    shift
    while [ $# -gt 0 ]; do
      case "$1" in
        -f | --project-directory | -p) shift 2 ;;
        *) break ;;
      esac
    done
    sub=${1:-}
    shift || true
    case "$sub" in
      version)
        stall compose-version
        flagged compose-version
        printf '%s\n' "${FAKE_VERSION_OUT:-Docker Compose version v2.35.1}"
        exit "${FAKE_VERSION_RC:-0}"
        ;;
      ps)
        case " $* " in
          *" --status "*)
            stall compose-ps-running
            flagged compose-ps-running
            ;;
        esac
        stall compose-ps
        flagged compose-ps
        [ -z "${FAKE_COMPOSE_PS_RC:-}" ] || exit "$FAKE_COMPOSE_PS_RC"
        if [ -n "${FAKE_FLAG_DIR:-}" ] && [ -f "$FAKE_FLAG_DIR/compose-ps-out" ]; then
          cat "$FAKE_FLAG_DIR/compose-ps-out"
        else
          printf '%s' "${FAKE_COMPOSE_PS_OUT:-}"
        fi
        ;;
      down)
        stall down
        flagged down
        # a successful down removes the containers a test made appear through FAKE_FLAG_DIR/compose-ps-out
        [ "${FAKE_DOWN_RC:-0}" -ne 0 ] || { [ -z "${FAKE_FLAG_DIR:-}" ] || rm -f "$FAKE_FLAG_DIR/compose-ps-out"; }
        exit "${FAKE_DOWN_RC:-0}"
        ;;
      config) exit "${FAKE_CONFIG_RC:-0}" ;;
      *) exit 0 ;;
    esac
    ;;
  *) exit 0 ;;
esac
