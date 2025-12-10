#!/bin/bash

# Script to run the v3 Confluent Export Utility
# Usage: ./export.sh [confluent-schema-registry-url] [output-file]

CONFLUENT_URL="${1:-http://localhost:8081/}"
OUTPUT_FILE="${2:-confluent-schema-registry-export.zip}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"

echo "==================================================================="
echo "Confluent Schema Registry Export Utility (v3 format)"
echo "==================================================================="
echo "Confluent URL: $CONFLUENT_URL"
echo "Output file: $OUTPUT_FILE"
echo "Project directory: $PROJECT_DIR"
echo ""

# Find the quarkus-app directory (fast-jar layout)
QUARKUS_APP_DIR="$PROJECT_DIR/target/quarkus-app"

# Build the project if needed
if [ ! -d "$QUARKUS_APP_DIR" ]; then
    echo "Building export utility..."
    cd "$PROJECT_DIR"
    mvn clean package -Pprod -DskipTests
    if [ $? -ne 0 ]; then
        echo "Build failed!"
        exit 1
    fi
fi

if [ ! -f "$QUARKUS_APP_DIR/quarkus-run.jar" ]; then
    echo "Error: Could not find quarkus-run.jar in $QUARKUS_APP_DIR/"
    exit 1
fi

echo "Using: $QUARKUS_APP_DIR/quarkus-run.jar"
echo ""
echo "Exporting with v3 format..."
echo ""

# Run the export utility (now using v3 format by default)
java -jar "$QUARKUS_APP_DIR/quarkus-run.jar" "$CONFLUENT_URL" --output "$OUTPUT_FILE"

if [ $? -eq 0 ]; then
    echo ""
    echo "==================================================================="
    echo "Export complete: $OUTPUT_FILE"
    echo "==================================================================="
    ls -lh "$OUTPUT_FILE"
fi