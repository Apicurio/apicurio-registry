#!/bin/sh
#
# Re-augments the Apicurio Registry application so that the jars found in
# /deployments/quarkus-app/providers become part of the application (Quarkus mutable-jar
# re-augmentation, the same mechanism as Keycloak's "kc.sh build").
#
# Usage (typically from a Dockerfile deriving from apicurio/apicurio-registry:VERSION-mutable):
#   /deployments/build.sh [--prune]
#
#   --prune   remove lib/deployment afterwards. The application can then no longer be
#             re-augmented, but the Quarkus deployment jars disappear from the image file system.
#
# NOTE: this file is filtered by Maven; do not use the ${...} syntax in it.
set -eu

APP_DIR=/deployments/quarkus-app
PROVIDERS_DIR=$APP_DIR/providers
DEPLOYMENT_DIR=$APP_DIR/lib/deployment
PRUNE=false

for arg in "$@"; do
  case "$arg" in
    --prune) PRUNE=true ;;
    -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

if [ ! -d "$DEPLOYMENT_DIR" ]; then
  echo "ERROR: $DEPLOYMENT_DIR is missing: this image has already been pruned and cannot be re-augmented." >&2
  exit 1
fi

echo "Provider jars in $PROVIDERS_DIR:"
if ls "$PROVIDERS_DIR"/*.jar >/dev/null 2>&1; then
  ls -1 "$PROVIDERS_DIR"/*.jar
else
  echo "  (none)"
fi

# Re-augmentation can update entries under both quarkus/ and lib/, so run it on a scratch
# copy and synchronize the complete generated application output back to the image.
WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT
cp -a "$APP_DIR" "$WORK_DIR/quarkus-app"

echo "Re-augmenting the application..."
# JAVA_OPTS_APPEND is honoured so that e.g. -Xmx can be tuned for the build step.
# shellcheck disable=SC2086
java ${JAVA_OPTS_APPEND:-} -Dquarkus.launch.rebuild=true -jar "$WORK_DIR/quarkus-app/quarkus-run.jar"

rm -rf "$APP_DIR/quarkus" "$APP_DIR/lib"
cp -a "$WORK_DIR/quarkus-app/quarkus" "$APP_DIR/quarkus"
cp -a "$WORK_DIR/quarkus-app/lib" "$APP_DIR/lib"
cp -a "$WORK_DIR/quarkus-app/quarkus-run.jar" "$APP_DIR/quarkus-run.jar"
cp -a "$WORK_DIR/quarkus-app/quarkus-app-dependencies.txt" "$APP_DIR/quarkus-app-dependencies.txt" 2>/dev/null || true

if [ "$PRUNE" = true ]; then
  echo "Pruning $DEPLOYMENT_DIR ($(du -sh "$DEPLOYMENT_DIR" | cut -f1))"
  rm -rf "$DEPLOYMENT_DIR"
fi
echo "Done."
