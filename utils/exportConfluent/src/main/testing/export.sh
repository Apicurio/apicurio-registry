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

# Find the runner jar
RUNNER_JAR=$(ls "$PROJECT_DIR/target/"apicurio-registry-utils-exportConfluent-*-runner.jar 2>/dev/null | head -1)

# Build the project if needed
if [ -z "$RUNNER_JAR" ]; then
    echo "Building export utility..."
    cd "$PROJECT_DIR"
    mvn clean package -Pprod -DskipTests
    if [ $? -ne 0 ]; then
        echo "Build failed!"
        exit 1
    fi
    # Find the jar again after build
    RUNNER_JAR=$(ls "$PROJECT_DIR/target/"apicurio-registry-utils-exportConfluent-*-runner.jar 2>/dev/null | head -1)
fi

if [ -z "$RUNNER_JAR" ]; then
    echo "Error: Could not find runner jar in $PROJECT_DIR/target/"
    exit 1
fi

echo "Using jar: $RUNNER_JAR"
echo ""
echo "Exporting with v3 format..."
echo ""

# Run the export utility (now using v3 format by default)
java -jar "$RUNNER_JAR" "$CONFLUENT_URL" --output "$OUTPUT_FILE"

if [ $? -eq 0 ]; then
    echo ""
    echo "==================================================================="
    echo "Export complete: $OUTPUT_FILE"
    echo "==================================================================="
    ls -lh "$OUTPUT_FILE"
fi