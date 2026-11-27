#!/usr/bin/env bash
# Tests of clone_dir (clone-dir.sh) on THIS operating system: the same cases run on macOS (BSD cp, APFS clones) and on Linux (GNU cp),
# and the copy semantics the real-tooling fixture relies on are compared there, not assumed: the tree, the modes, the links (a relative one
# and an absolute one, never followed), no nested copy, and no partial destination when the fast copy fails part way.
#
# Usage: bash tests/ui/test/clone-dir-test.sh
# Needs bash 3.2 or later, cp, find, cksum, readlink and mktemp. Nothing outside a temporary directory is read or written.
set -uo pipefail

HERE=$(cd "$(dirname "$0")" && pwd -P)
# shellcheck source=tests/ui/test/clone-dir.sh
. "$HERE/clone-dir.sh"

TMP=$(cd "$(mktemp -d "${TMPDIR:-/tmp}/clone-dir-test.XXXXXX")" && pwd -P)
trap 'rm -rf "$TMP"' EXIT
failures=0
passed=0
check() { # check <description> <expected> <actual>
  if [ "$2" = "$3" ]; then
    passed=$((passed + 1))
    printf '  ok    %s\n' "$1"
  else
    failures=$((failures + 1))
    printf '  FAIL  %s\n        expected: %s\n        actual:   %s\n' "$1" "$2" "$3"
  fi
}

# beside <name prefix>: the entries of the temporary directory whose names start with it, space separated
beside() {
  local entry out=""
  for entry in "$TMP/$1"*; do
    [ -e "$entry" ] || [ -L "$entry" ] || continue
    out="$out${entry#"$TMP"/} "
  done
  printf '%s' "${out% }"
}

# snapshot <dir>: every entry with its kind, whether it is executable, its link target or its checksum
snapshot() {
  (
    cd "$1" || exit 1
    find . | LC_ALL=C sort | while IFS= read -r entry; do
      if [ -L "$entry" ]; then
        printf '%s link -> %s\n' "$entry" "$(readlink "$entry")"
      elif [ -d "$entry" ]; then
        printf '%s dir\n' "$entry"
      else
        printf '%s file x=%s %s\n' "$entry" "$([ -x "$entry" ] && echo yes || echo no)" "$(cksum < "$entry")"
      fi
    done
  )
}

mkdir -p "$TMP/src/bin" "$TMP/src/empty" "$TMP/src/deep/x/y" "$TMP/outside"
printf 'alpha\n' > "$TMP/src/a.txt"
printf 'spaced\n' > "$TMP/src/with space.txt"
printf '#!/bin/sh\necho tool\n' > "$TMP/src/bin/tool"
chmod 755 "$TMP/src/bin/tool"
printf 'deep\n' > "$TMP/src/deep/x/y/z.txt"
ln -s a.txt "$TMP/src/link"
ln -s ../bin/tool "$TMP/src/deep/link2"
ln -s "$TMP/outside" "$TMP/src/absolute"
printf 'outside\n' > "$TMP/outside/file"
snapshot "$TMP/src" > "$TMP/src.snapshot"

printf 'clone_dir on %s with cp: %s\n' "$(uname -s)" "$(cp --version 2> /dev/null | head -1 || true)"
clone_dir "$TMP/src" "$TMP/ok"
check "the tree, the modes and the links are those of the source" "$(cat "$TMP/src.snapshot")" "$(snapshot "$TMP/ok")"
check "  no copy of the source inside the destination" no "$([ -e "$TMP/ok/src" ] && echo yes || echo no)"
check "  the absolute link is still a link to the same place, not a copy of what it points to" "$TMP/outside" "$(readlink "$TMP/ok/absolute")"
check "  the destination is independent of the source (a change in one is not in the other)" "alpha" "$(printf 'changed\n' > "$TMP/ok/a.txt" && cat "$TMP/src/a.txt")"
check "  nothing is left beside the destination" "ok" "$(beside ok)"

mkdir "$TMP/exists"
printf 'keep\n' > "$TMP/exists/mine"
clone_dir "$TMP/src" "$TMP/exists" 2> /dev/null
check "an existing destination is refused (status 2) and left alone" "2 mine" "$? $(ls "$TMP/exists")"
clone_dir "$TMP/no-such-source" "$TMP/never" 2> /dev/null
check "a missing source is refused (status 2) and creates nothing" "2 no" "$? $([ -e "$TMP/never" ] && echo yes || echo no)"

# A fast copy that fails part way: the first call that asks for a clone or a reflink leaves half a destination and fails; any other call
# is the real cp. The ordinary copy that follows must still produce the whole tree, not a nested one.
mkdir "$TMP/shadow"
REAL_CP=$(command -v cp)
cat > "$TMP/shadow/cp" << SCRIPT
#!/bin/sh
case " \$* " in
  *" -Rc "* | *--reflink=auto*)
    for last; do :; done
    mkdir -p "\$last"
    cp_part=\$(ls "$TMP/src" | head -1)
    $REAL_CP -R "$TMP/src/\$cp_part" "\$last/" 2> /dev/null
    exit 1
    ;;
esac
exec $REAL_CP "\$@"
SCRIPT
chmod 755 "$TMP/shadow/cp"
PATH="$TMP/shadow:$PATH" clone_dir "$TMP/src" "$TMP/fallback"
check "a fast copy that fails part way is followed by a whole ordinary copy" "0 $(cksum < "$TMP/src.snapshot")" "$? $(snapshot "$TMP/fallback" | cksum)"
check "  not nested in the half-made destination" no "$([ -e "$TMP/fallback/src" ] && echo yes || echo no)"
check "  and no remains of the failed attempt" "fallback" "$(beside fallback)"

# Every copy failing: a failure is reported and nothing is left at or beside the destination
cat > "$TMP/shadow/cp" << 'SCRIPT'
#!/bin/sh
for last; do :; done
mkdir -p "$last"
exit 1
SCRIPT
PATH="$TMP/shadow:$PATH" clone_dir "$TMP/src" "$TMP/doomed" 2> /dev/null
check "when every copy fails the status is 1 and nothing remains" "1 " "$? $(beside doomed)"

printf '\n%s passed, %s failed\n' "$passed" "$failures"
[ "$failures" -eq 0 ]
