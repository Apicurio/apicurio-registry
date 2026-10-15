#!/usr/bin/env bash
#
# Whether commit $SHA is newer than the commits the given published images were
# built from. See action.yaml for why; tests in newer-than-published.test.sh.
#
# Usage: SHA=<commit> GITHUB_REPOSITORY=<owner/repo> newer-than-published.sh <image>...
#
# Prints one line per image and writes publish=true or publish=false to
# $GITHUB_OUTPUT. For each image, the commit in its
# org.opencontainers.image.revision label is compared with $SHA:
#   ahead or identical -> publish
#   behind             -> skip
#   diverged           -> publish (rewritten history: the branch wins)
#   nothing published, no label, or a label naming a commit this repository
#   doesn't have (the base image's, on images from before the label) -> publish
# One image built from a newer commit skips them all: the images are published
# by parallel jobs, so a newer run can have published some of them and failed
# on the rest.
#
# Any other failure, reading an image or calling the compare API, exits
# non-zero without writing an output. Publishing anyway could move a tag back,
# and the failed job gets retried.

set -euo pipefail

publish=true
err=$(mktemp)
trap 'rm -f "$err"' EXIT

for image in "$@"; do
  # skopeo exits 2 when the image doesn't exist. Before 1.16 (ubuntu-24.04 has
  # 1.13) it exits 1, and only the registry's error code tells.
  rc=0
  published=$(skopeo inspect --retry-times 3 --override-os linux --override-arch amd64 \
      --format '{{ index .Labels "org.opencontainers.image.revision" }}' "docker://$image" 2>"$err") || rc=$?
  if [ "$rc" -eq 2 ] || { [ "$rc" -ne 0 ] && grep -q "manifest unknown" "$err"; }; then
    echo "$image: nothing published. Publish"
    continue
  elif [ "$rc" -ne 0 ]; then
    echo "::error::Could not read $image: $(cat "$err")"
    exit 1
  elif [ -z "$published" ]; then
    echo "$image: no revision label. Publish"
    continue
  fi

  # The compare API answers 404 for a commit this repository doesn't have.
  rc=0
  status=$(gh api "repos/$GITHUB_REPOSITORY/compare/$published...$SHA" --jq .status 2>"$err") || rc=$?
  if [ "$rc" -ne 0 ]; then
    if grep -q "HTTP 404" "$err"; then
      status=unknown
    else
      echo "::error::Could not compare $published with $SHA: $(cat "$err")"
      exit 1
    fi
  fi

  case "$status" in
    behind) publish=false; verdict="this commit is behind. Skip" ;;
    unknown) verdict="a commit this repository doesn't have. Publish" ;;
    *) verdict="this commit is $status. Publish" ;;
  esac
  echo "$image: built from $published, $verdict"
done

echo "Publish: $publish"
echo "publish=$publish" >> "$GITHUB_OUTPUT"
