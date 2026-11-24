#!/usr/bin/env bash
# Lists the examples that verify-examples.yaml runs, as a JSON matrix on stdout:
#   [{"name": "per-resource-authorization", "timeout": 20, "build": false, "variant": "",
#     "selfManaged": false, "releasedImages": "", "mutableImage": false, "compose": true,
#     "toolchains": "", "suite": "", "playwrightDir": ""}, ...]
#
# An example without a Compose file is run only when it is CI_SELF_MANAGED: demo.sh then brings its
# own platform (for example a kind cluster), and the Compose steps are skipped.
#
# An example is run when examples/<name>/ has an executable demo.sh and a Compose file. Optional
# settings come from examples/<name>/ci.env (KEY=value lines, # comments):
#   CI_ENABLED=false       skip the example in CI (say why in a comment)
#   CI_TIMEOUT_MINUTES=N   job timeout, default 20
#   CI_NEEDS_BUILD=true    set up a JDK and the Maven cache, then run the example's build.sh, which
#                          builds its Java parts from this checkout (snapshots are not published)
#   CI_MATRIX=a b c        one job per value, each running demo.sh with CI_VARIANT set to it (for an
#                          example too long for one job); the job name gets the value appended
#   CI_SELF_MANAGED=true   demo.sh starts and stops its own containers (for an example that must
#                          prepare something first); the runner only checks the images and cleans up
#   CI_NEEDS_MUTABLE_IMAGE=true  load this commit's re-augmentable registry image, as
#                          REGISTRY_MUTABLE_IMAGE (for an example that adds Java providers to it)
#   CI_RELEASED_IMAGES=i j released Apicurio Registry images the example runs on purpose, in addition to
#                          this commit's (a migration from an older release); exempt from the image check
#   CI_TOOLCHAINS=go node python  set up these toolchains besides the JDK (for an example with clients
#                          in other languages); each value is one of go, node, python
#   CI_SUITE=path          an executable script of the example (relative, no ..) that verify-examples.yaml runs ONCE per job after both
#                          demo.sh runs, with the example's stack still up, as the example's own checks beyond the demo (its unit,
#                          lifecycle, scenario and browser suites). The script's exit status is the step's.
#                          Not combinable with CI_MATRIX (the suite would run once per variant)
#   CI_PLAYWRIGHT_DIR=dir  a directory of the example (relative, no ..) with a locked Playwright package (package.json and
#                          package-lock.json). The runner installs its locked packages with `npm ci` and the operating system packages
#                          its Chromium needs (`playwright install-deps chromium`, with sudo, in the workflow and never in the
#                          example's scripts). Needs CI_TOOLCHAINS to include node
# Every other line is exported to `docker compose` and demo.sh (see verify-examples.yaml).
#
# Usage: .github/scripts/discover-examples.sh [examples-dir]
set -euo pipefail

dir=${1:-examples}
entries=()

# contained_regular <example> <relative path>: the path names a regular file (not a link) whose every directory is a real directory
# of the example, so the file cannot lead out of it through a link; contained_dir <example> <relative path> is the same for a directory
contained_dir() {
  local root physical
  root=$(cd -P "$1" && pwd -P) || return 1
  [ ! -L "$1/$2" ] || return 1
  physical=$(cd -P "$1/$2" 2> /dev/null && pwd -P) || return 1
  [ "$physical" = "$root/$2" ]
}
contained_regular() {
  local parent=${2%/*}
  [ -f "$1/$2" ] && [ ! -L "$1/$2" ] || return 1
  case $2 in */*) contained_dir "$1" "$parent" ;; *) return 0 ;; esac
}

for demo in "$dir"/*/demo.sh; do
  [ -e "$demo" ] || continue
  example=$(dirname "$demo")
  name=$(basename "$example")

  compose=
  for f in compose.yaml compose.yml docker-compose.yaml docker-compose.yml; do
    [ -f "$example/$f" ] && compose=$f && break
  done
  if [ ! -x "$demo" ]; then
    echo "::error::$demo is not executable (git update-index --chmod=+x)" >&2
    exit 1
  fi

  enabled=true
  timeout=20
  build=false
  matrix=""
  managed=false
  released=""
  mutable=false
  toolchains=""
  suite=""
  playwright_dir=""
  if [ -f "$example/ci.env" ]; then
    value() { sed -n "s/^$1=//p" "$example/ci.env" | tail -1; }
    enabled=$(value CI_ENABLED); enabled=${enabled:-true}
    timeout=$(value CI_TIMEOUT_MINUTES); timeout=${timeout:-20}
    build=$(value CI_NEEDS_BUILD); build=${build:-false}
    matrix=$(value CI_MATRIX)
    managed=$(value CI_SELF_MANAGED); managed=${managed:-false}
    released=$(value CI_RELEASED_IMAGES)
    mutable=$(value CI_NEEDS_MUTABLE_IMAGE); mutable=${mutable:-false}
    toolchains=$(value CI_TOOLCHAINS)
    suite=$(value CI_SUITE)
    playwright_dir=$(value CI_PLAYWRIGHT_DIR)
  fi
  if [ "$managed" != true ] && [ "$managed" != false ]; then
    echo "::error::$name: CI_SELF_MANAGED must be true or false, got '$managed'" >&2
    exit 1
  fi
  # Without a Compose file, only a self-managed example can run (demo.sh brings its own platform,
  # for example a kind cluster)
  if [ -z "$compose" ] && [ "$managed" != true ]; then
    echo "::warning::$name has demo.sh but no Compose file, and is not CI_SELF_MANAGED; skipped" >&2
    continue
  fi
  has_compose=$([ -n "$compose" ] && echo true || echo false)
  if [ -n "$released" ] && ! [[ "$released" =~ ^([^ :]+:[^ ]+)( [^ :]+:[^ ]+)*$ ]]; then
    echo "::error::$name: CI_RELEASED_IMAGES must be space-separated image:tag values, got '$released'" >&2
    exit 1
  fi
  if [ "$mutable" != true ] && [ "$mutable" != false ]; then
    echo "::error::$name: CI_NEEDS_MUTABLE_IMAGE must be true or false, got '$mutable'" >&2
    exit 1
  fi
  if [ -n "$toolchains" ] && ! [[ "$toolchains" =~ ^(go|node|python)( (go|node|python))*$ ]]; then
    echo "::error::$name: CI_TOOLCHAINS must be space-separated values of go, node and python, got '$toolchains'" >&2
    exit 1
  fi
  # A path of the example, never an absolute one and never one that leaves it (the value reaches a shell step as a path)
  for pair in "CI_SUITE:$suite" "CI_PLAYWRIGHT_DIR:$playwright_dir"; do
    key=${pair%%:*}; path=${pair#*:}
    if [ -n "$path" ] && { ! [[ "$path" =~ ^[A-Za-z0-9_][A-Za-z0-9_./-]*$ ]] || [[ "$path" == */ ]] || [[ "/$path/" == */../* ]] || [[ "/$path/" == */./* ]]; }; then
      echo "::error::$name: $key must be a relative path inside the example (starting with a letter, digit or _, then letters, digits, _ . / -, no .. and no trailing /), got '$path'" >&2
      exit 1
    fi
  done
  if [ -n "$suite" ] && { ! contained_regular "$example" "$suite" || [ ! -x "$example/$suite" ]; }; then
    echo "::error::$name: CI_SUITE must name an executable regular file inside the example (no link), got '$suite'" >&2
    exit 1
  fi
  if [ -n "$suite" ] && [ -n "$matrix" ]; then
    echo "::error::$name: CI_SUITE cannot be combined with CI_MATRIX (the suite would run once per variant)" >&2
    exit 1
  fi
  if [ -n "$playwright_dir" ] && { ! contained_dir "$example" "$playwright_dir" || ! contained_regular "$example" "$playwright_dir/package.json" || ! contained_regular "$example" "$playwright_dir/package-lock.json"; }; then
    echo "::error::$name: CI_PLAYWRIGHT_DIR must be a real directory inside the example with a package.json and a package-lock.json that are regular files, got '$playwright_dir'" >&2
    exit 1
  fi
  if [ -n "$playwright_dir" ] && ! [[ " $toolchains " == *" node "* ]]; then
    echo "::error::$name: CI_PLAYWRIGHT_DIR needs CI_TOOLCHAINS to include node" >&2
    exit 1
  fi
  if [ -n "$playwright_dir" ] && [ -z "$suite" ]; then
    echo "::error::$name: CI_PLAYWRIGHT_DIR is only used by CI_SUITE; set CI_SUITE too" >&2
    exit 1
  fi
  if [ "$build" != true ] && [ "$build" != false ]; then
    echo "::error::$name: CI_NEEDS_BUILD must be true or false, got '$build'" >&2
    exit 1
  fi
  if [ "$build" = true ] && [ ! -x "$example/build.sh" ]; then
    echo "::error::$name: CI_NEEDS_BUILD=true needs an executable build.sh" >&2
    exit 1
  fi
  if ! [[ "$timeout" =~ ^[0-9]+$ ]]; then
    echo "::error::$name: CI_TIMEOUT_MINUTES must be a number, got '$timeout'" >&2
    exit 1
  fi
  if [ "$enabled" = false ]; then
    echo "$name: CI_ENABLED=false; skipped" >&2
    continue
  fi
  if [ -n "$matrix" ] && ! [[ "$matrix" =~ ^[a-z0-9-]+( [a-z0-9-]+)*$ ]]; then
    echo "::error::$name: CI_MATRIX must be space-separated values of [a-z0-9-], got '$matrix'" >&2
    exit 1
  fi
  # One entry per CI_MATRIX value, or a single entry with no variant
  for variant in ${matrix:-<none>}; do
    [ "$variant" = "<none>" ] && variant=""
    entries+=("$(jq -cn --arg n "$name" --argjson t "$timeout" --argjson b "$build" --arg v "$variant" \
      --argjson m "$managed" --arg r "$released" --argjson mi "$mutable" --argjson c "$has_compose" \
      --arg tc "$toolchains" --arg su "$suite" --arg pw "$playwright_dir" \
      '{name: $n, timeout: $t, build: $b, variant: $v, selfManaged: $m, releasedImages: $r, mutableImage: $mi, compose: $c, toolchains: $tc, suite: $su, playwrightDir: $pw}')")
  done
done

if [ ${#entries[@]} -eq 0 ]; then
  echo '[]'
else
  printf '%s\n' "${entries[@]}" | jq -cs .
fi
