#!/bin/bash

# =============================================================================
# Data Seeding Script for Search Performance Benchmark
# =============================================================================
# This script populates an Apicurio Registry instance with test data including:
# - Multiple groups
# - Artifacts with various names and descriptions
# - Labels (key-value pairs) on artifacts
# - Multiple versions with labels
#
# Usage: ./seed-data.sh <registry-url> [num-artifacts] [num-labels] [num-groups] [num-versions] [parallel-jobs]
# Example: ./seed-data.sh http://localhost:8080 10000 5 10 3 10
# =============================================================================

set -e

REGISTRY_URL="${1:-http://localhost:8080}"
NUM_ARTIFACTS="${2:-10000}"
NUM_LABELS="${3:-5}"
NUM_GROUPS="${4:-10}"
NUM_VERSIONS="${5:-3}"
PARALLEL_JOBS="${6:-10}"

API_URL="${REGISTRY_URL}/apis/registry/v3"

echo "=============================================="
echo "Apicurio Registry Data Seeding"
echo "=============================================="
echo "Registry URL: ${REGISTRY_URL}"
echo "Number of groups: ${NUM_GROUPS}"
echo "Number of artifacts: ${NUM_ARTIFACTS}"
echo "Labels per artifact: ${NUM_LABELS}"
echo "Versions per artifact: ${NUM_VERSIONS}"
echo "Parallel jobs: ${PARALLEL_JOBS}"
echo "=============================================="

# Wait for registry to be ready
echo "Waiting for registry to be ready..."
for i in {1..60}; do
    if curl -s "${REGISTRY_URL}/health/ready" | grep -q "UP"; then
        echo "Registry is ready!"
        break
    fi
    if [ $i -eq 60 ]; then
        echo "Registry not ready after 60 seconds, exiting."
        exit 1
    fi
    sleep 1
done

# Sample JSON Schema for test artifacts
SCHEMA_TEMPLATE='{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "ARTIFACT_NAME",
  "description": "ARTIFACT_DESC",
  "properties": {
    "id": { "type": "string" },
    "name": { "type": "string" },
    "timestamp": { "type": "string", "format": "date-time" }
  },
  "required": ["id"]
}'

# Environment labels for variety
ENVS=("dev" "staging" "prod" "qa" "test")
TEAMS=("platform" "backend" "frontend" "data" "infra" "security" "devops")
DOMAINS=("orders" "users" "products" "payments" "inventory" "shipping" "notifications")
STATUSES=("active" "deprecated" "experimental" "stable" "legacy")

# Function to create a group
create_group() {
    local group_id=$1
    local description=$2

    curl -s -X POST "${API_URL}/groups" \
        -H "Content-Type: application/json" \
        -d "{
            \"groupId\": \"${group_id}\",
            \"description\": \"${description}\"
        }" > /dev/null 2>&1 || true
}

# Function to create an artifact with labels
create_artifact() {
    local group_id=$1
    local artifact_id=$2
    local name=$3
    local description=$4
    local labels_json=$5

    local schema=$(echo "${SCHEMA_TEMPLATE}" | sed "s/ARTIFACT_NAME/${name}/g" | sed "s/ARTIFACT_DESC/${description}/g")

    local payload="{
        \"artifactId\": \"${artifact_id}\",
        \"artifactType\": \"JSON\",
        \"name\": \"${name}\",
        \"description\": \"${description}\",
        \"labels\": ${labels_json},
        \"firstVersion\": {
            \"version\": \"1.0.0\",
            \"content\": {
                \"contentType\": \"application/json\",
                \"content\": $(echo "${schema}" | jq -c . | jq -Rs .)
            }
        }
    }"

    curl -s -X POST "${API_URL}/groups/${group_id}/artifacts" \
        -H "Content-Type: application/json" \
        -d "${payload}" > /dev/null 2>&1
}

# Function to create a version with labels
create_version() {
    local group_id=$1
    local artifact_id=$2
    local version=$3
    local labels_json=$4

    local schema=$(echo "${SCHEMA_TEMPLATE}" | sed "s/ARTIFACT_NAME/${artifact_id}-v${version}/g" | sed "s/ARTIFACT_DESC/Version ${version}/g")

    curl -s -X POST "${API_URL}/groups/${group_id}/artifacts/${artifact_id}/versions" \
        -H "Content-Type: application/json" \
        -d "{
            \"version\": \"${version}\",
            \"content\": {
                \"contentType\": \"application/json\",
                \"content\": $(echo "${schema}" | jq -c . | jq -Rs .)
            }
        }" > /dev/null 2>&1 || true
}

# Function to generate random labels JSON
generate_labels() {
    local num_labels=$1
    local labels="{"
    local first=true

    for ((l=1; l<=num_labels; l++)); do
        if [ "$first" = true ]; then
            first=false
        else
            labels="${labels},"
        fi

        case $((l % 5)) in
            0) labels="${labels}\"env\": \"${ENVS[$((RANDOM % ${#ENVS[@]}))]}\"";;
            1) labels="${labels}\"team\": \"${TEAMS[$((RANDOM % ${#TEAMS[@]}))]}\"";;
            2) labels="${labels}\"domain\": \"${DOMAINS[$((RANDOM % ${#DOMAINS[@]}))]}\"";;
            3) labels="${labels}\"status\": \"${STATUSES[$((RANDOM % ${#STATUSES[@]}))]}\"";;
            4) labels="${labels}\"version-tag\": \"v$((RANDOM % 10)).$((RANDOM % 10))\"";;
        esac
    done

    labels="${labels}}"
    echo "${labels}"
}

# Create groups
echo ""
echo "Creating ${NUM_GROUPS} groups..."
for ((g=1; g<=NUM_GROUPS; g++)); do
    group_id="benchmark-group-${g}"
    create_group "${group_id}" "Benchmark test group ${g} for search performance testing"
    printf "\rGroups created: %d/%d" $g $NUM_GROUPS
done
echo ""

# Create artifacts with labels using parallel processing
echo ""
echo "Creating ${NUM_ARTIFACTS} artifacts with ${NUM_LABELS} labels each (${PARALLEL_JOBS} parallel jobs)..."
artifacts_per_group=$((NUM_ARTIFACTS / NUM_GROUPS))

# Export functions and variables for use in subshells
export API_URL NUM_LABELS NUM_VERSIONS
export -f create_artifact create_version generate_labels

# Create a temporary file to track progress
PROGRESS_FILE=$(mktemp)
echo "0" > "${PROGRESS_FILE}"

# Function to create a single artifact with versions (for parallel execution)
create_artifact_with_versions() {
    local group_id=$1
    local artifact_count=$2
    local progress_file=$3

    # Generate artifact details (use artifact_count as seed for deterministic but varied data)
    local domain_idx=$((artifact_count % 7))
    local domains=("orders" "users" "products" "payments" "inventory" "shipping" "notifications")
    local domain="${domains[$domain_idx]}"
    local artifact_id="${domain}-service-api-${artifact_count}"
    local domain_cap="$(echo "${domain}" | awk '{print toupper(substr($0,1,1)) tolower(substr($0,2))}')"
    local name="${domain_cap} Service API ${artifact_count}"
    local description="API schema for ${domain} service - artifact ${artifact_count} with search optimization testing"

    # Generate labels inline
    local envs=("dev" "staging" "prod" "qa" "test")
    local teams=("platform" "backend" "frontend" "data" "infra" "security" "devops")
    local statuses=("active" "deprecated" "experimental" "stable" "legacy")

    local labels="{"
    labels="${labels}\"env\": \"${envs[$((artifact_count % 5))]}\""
    labels="${labels},\"team\": \"${teams[$((artifact_count % 7))]}\""
    labels="${labels},\"domain\": \"${domain}\""
    labels="${labels},\"status\": \"${statuses[$((artifact_count % 5))]}\""
    labels="${labels},\"version-tag\": \"v$((artifact_count % 10)).$((artifact_count % 5))\""
    labels="${labels}}"

    # Create artifact
    local schema='{
      "$schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "title": "'"${name}"'",
      "description": "'"${description}"'",
      "properties": {
        "id": { "type": "string" },
        "name": { "type": "string" },
        "timestamp": { "type": "string", "format": "date-time" }
      },
      "required": ["id"]
    }'

    local payload="{
        \"artifactId\": \"${artifact_id}\",
        \"artifactType\": \"JSON\",
        \"name\": \"${name}\",
        \"description\": \"${description}\",
        \"labels\": ${labels},
        \"firstVersion\": {
            \"version\": \"1.0.0\",
            \"content\": {
                \"contentType\": \"application/json\",
                \"content\": $(echo "${schema}" | jq -c . | jq -Rs .)
            }
        }
    }"

    curl -s -X POST "${API_URL}/groups/${group_id}/artifacts" \
        -H "Content-Type: application/json" \
        -d "${payload}" > /dev/null 2>&1

    # Create additional versions
    for ((v=2; v<=NUM_VERSIONS; v++)); do
        local version="${v}.0.0"
        local vschema='{
          "$schema": "http://json-schema.org/draft-07/schema#",
          "type": "object",
          "title": "'"${artifact_id}-v${version}"'",
          "description": "Version '"${version}"'",
          "properties": {
            "id": { "type": "string" },
            "name": { "type": "string" },
            "timestamp": { "type": "string", "format": "date-time" }
          },
          "required": ["id"]
        }'

        curl -s -X POST "${API_URL}/groups/${group_id}/artifacts/${artifact_id}/versions" \
            -H "Content-Type: application/json" \
            -d "{
                \"version\": \"${version}\",
                \"content\": {
                    \"contentType\": \"application/json\",
                    \"content\": $(echo "${vschema}" | jq -c . | jq -Rs .)
                }
            }" > /dev/null 2>&1 || true
    done

    # Update progress counter atomically
    local current
    current=$(cat "${progress_file}")
    echo $((current + 1)) > "${progress_file}"
}

export -f create_artifact_with_versions

# Build list of all artifacts to create
artifact_count=0
JOBS_FILE=$(mktemp)
for ((g=1; g<=NUM_GROUPS; g++)); do
    group_id="benchmark-group-${g}"
    for ((a=1; a<=artifacts_per_group; a++)); do
        artifact_count=$((artifact_count + 1))
        echo "${group_id} ${artifact_count} ${PROGRESS_FILE}" >> "${JOBS_FILE}"
    done
done

# Run in parallel batches
BATCH_SIZE=$((PARALLEL_JOBS * 10))
total_artifacts=${artifact_count}
processed=0

while IFS= read -r line || [ -n "$line" ]; do
    group_id=$(echo "$line" | cut -d' ' -f1)
    art_num=$(echo "$line" | cut -d' ' -f2)

    create_artifact_with_versions "$group_id" "$art_num" "${PROGRESS_FILE}" &

    processed=$((processed + 1))

    # Limit parallel jobs
    if [ $((processed % PARALLEL_JOBS)) -eq 0 ]; then
        wait
        current=$(cat "${PROGRESS_FILE}")
        printf "\rArtifacts created: %d/%d" $current $total_artifacts
    fi
done < "${JOBS_FILE}"

# Wait for remaining jobs
wait
current=$(cat "${PROGRESS_FILE}")
printf "\rArtifacts created: %d/%d" $current $total_artifacts
echo ""

# Cleanup temp files
rm -f "${PROGRESS_FILE}" "${JOBS_FILE}"

echo ""
echo "=============================================="
echo "Data seeding complete!"
echo "Created: ${NUM_GROUPS} groups"
echo "Created: ${total_artifacts} artifacts"
echo "Created: $((total_artifacts * NUM_VERSIONS)) total versions"
echo "=============================================="
