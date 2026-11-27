# shellcheck shell=bash
# clone_dir <source directory> <destination>
# Copies a directory tree with its links, modes and times, as cheaply as the file system allows, and never leaves a half-filled
# destination behind. The fast copy (an APFS clone with `cp -Rc` on macOS, a reflink copy with GNU `cp --reflink=auto` elsewhere) is made
# beside the destination under a name of its own; only a complete copy is renamed into place. When the fast copy fails part way, the
# remains are removed and an ordinary copy is made into the same absent name: an ordinary `cp -R src dst` into a destination that a
# failed fast copy had half created would instead copy the tree INTO it (dst/src) and report success.
# Status: 0 copied; 2 the source is not a directory or the destination already exists; 1 the copy failed (nothing is left at the destination).
clone_dir() {
  local src=$1 dst=$2 part
  if [ ! -d "$src" ] || [ -e "$dst" ] || [ -L "$dst" ]; then
    printf 'clone_dir: needs an existing source directory and an absent destination: %s -> %s\n' "$src" "$dst" >&2
    return 2
  fi
  part=$dst.partial.$$
  if [ -e "$part" ] || [ -L "$part" ]; then
    printf 'clone_dir: %s is in the way\n' "$part" >&2
    return 2
  fi
  case $(uname -s) in
    Darwin) cp -Rc "$src" "$part" 2> /dev/null ;;
    *) cp -R -P -p --reflink=auto "$src" "$part" 2> /dev/null ;;
  esac || {
    rm -rf "$part"
    [ ! -e "$part" ] && [ ! -L "$part" ] || return 1
    cp -R -P -p "$src" "$part" || {
      rm -rf "$part"
      return 1
    }
  }
  mv "$part" "$dst" || {
    rm -rf "$part"
    return 1
  }
}
