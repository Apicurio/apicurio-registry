#!/bin/sh
set -eu

ARTIFACTS_DIR="${ARTIFACTS_DIR:-artifacts}"

copy_artifacts() {
    source_dir="$1"
    destination_dir="$ARTIFACTS_DIR/$source_dir"

    if [ -d "$source_dir" ]; then
        mkdir -p "$destination_dir"
        cp -R "$source_dir"/. "$destination_dir"/
        echo "Collected $source_dir"
    fi
}

echo "Collecting test logs and reports"

for module in integration-tests utils/extra-tests; do
    for artifact in target/failsafe-reports target/surefire-reports target/logs; do
        copy_artifacts "$module/$artifact"
    done
done
