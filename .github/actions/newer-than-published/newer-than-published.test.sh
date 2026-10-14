#!/usr/bin/env bash
#
# Tests for newer-than-published.sh, with skopeo and gh replaced by stubs. Plain
# bash, like scalpel-summary.test.sh.
#
# Usage: bash .github/actions/newer-than-published/newer-than-published.test.sh

set -uo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
subject="$script_dir/newer-than-published.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# The stubs answer from $work/images ("<image> <label>", "<image> -" for no label,
# "<image> !<exit code>", or "<image> !1-unknown" for skopeo < 1.16 not finding it)
# and $work/compare ("<published commit> <status>", or "<published commit> !<HTTP status>").
mkdir "$work/bin"
cat > "$work/bin/skopeo" <<'EOF'
#!/usr/bin/env bash
image=${!##docker://}
line=$(grep -F "$image " "$STUBS/images") || { echo "skopeo stub: no entry for $image" >&2; exit 99; }
answer=${line#* }
case "$answer" in
  !1-unknown) echo "reading manifest latest-snapshot in $image: manifest unknown" >&2; exit 1 ;;
  !*) echo "skopeo stub: failing" >&2; exit "${answer#!}" ;;
  -) echo "" ;;
  *) echo "$answer" ;;
esac
EOF
cat > "$work/bin/gh" <<'EOF'
#!/usr/bin/env bash
range=${2##*/compare/}
published=${range%%...*}
[ "$range" = "$published...$SHA" ] || { echo "gh stub: unexpected $2" >&2; exit 99; }
line=$(grep -F "$published " "$STUBS/compare") || { echo "gh stub: no entry for $published" >&2; exit 99; }
answer=${line#* }
case "$answer" in
  !*) echo "{\"message\":\"stub\"}"; echo "gh: stub (HTTP ${answer#!})" >&2; exit 1 ;;
  *) echo "$answer" ;;
esac
EOF
chmod +x "$work/bin/skopeo" "$work/bin/gh"

pass=0
fail=0

# check <name> <expected: true | false | error> <images file> <compare file> <image>...
check() {
  local name=$1 expected=$2 images=$3 compare=$4 rc actual
  shift 4
  printf '%b' "$images" > "$work/images"
  printf '%b' "$compare" > "$work/compare"
  : > "$work/output"
  STUBS=$work SHA=c0ffee GITHUB_REPOSITORY=Apicurio/apicurio-registry GITHUB_OUTPUT="$work/output" \
    PATH="$work/bin:$PATH" bash "$subject" "$@" > "$work/stdout" 2>&1
  rc=$?
  if [ "$rc" -ne 0 ]; then
    actual=error
    if [ -s "$work/output" ]; then
      actual="error, but wrote $(cat "$work/output")"
    fi
  else
    actual=$(sed -n 's/^publish=//p' "$work/output")
  fi
  if [ "$actual" = "$expected" ]; then
    pass=$((pass + 1))
  else
    echo "FAIL $name: expected $expected, got ${actual:-no output} (exit $rc)"
    sed 's/^/      /' "$work/stdout"
    fail=$((fail + 1))
  fi
}

check "ahead" true "app a1\n" "a1 ahead\n" app
check "identical" true "app a1\n" "a1 identical\n" app
check "behind" false "app a1\n" "a1 behind\n" app
check "diverged" true "app a1\n" "a1 diverged\n" app
check "nothing published" true "app !2\n" "" app
check "nothing published, skopeo before 1.16" true "app !1-unknown\n" "" app
check "no revision label" true "app -\n" "" app
check "label naming an unknown commit" true "app base1\n" "base1 !404\n" app
check "image unreadable" error "app !1\n" "" app
check "compare API error" error "app a1\n" "a1 !502\n" app
check "compare API rate limit" error "app a1\n" "a1 !403\n" app
check "all images ahead" true "app a1\nui u1\n" "a1 ahead\nu1 identical\n" app ui
check "one image behind" false "app a1\nui u1\n" "a1 ahead\nu1 behind\n" app ui
check "one image behind, then one ahead" false "app a1\nui u1\n" "a1 behind\nu1 ahead\n" app ui
check "behind, then an unreadable image" error "app a1\nui !1\n" "a1 behind\n" app ui
check "one image never published" true "app a1\nui !2\n" "a1 ahead\n" app ui

echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
