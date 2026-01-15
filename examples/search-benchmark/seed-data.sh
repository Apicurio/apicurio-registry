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
# Usage: ./seed-data.sh <registry-url> [num-artifacts] [num-labels-per-artifact]
# Example: ./seed-data.sh http://localhost:8080 1000 5
# =============================================================================

set -e

REGISTRY_URL="${1:-http://localhost:8080}"
NUM_ARTIFACTS="${2:-500}"
NUM_LABELS="${3:-5}"
NUM_GROUPS="${4:-10}"
NUM_VERSIONS="${5:-3}"

API_URL="${REGISTRY_URL}/apis/registry/v3"

echo "=============================================="
echo "Apicurio Registry Data Seeding"
echo "=============================================="
echo "Registry URL: ${REGISTRY_URL}"
echo "Number of groups: ${NUM_GROUPS}"
echo "Number of artifacts: ${NUM_ARTIFACTS}"
echo "Labels per artifact: ${NUM_LABELS}"
echo "Versions per artifact: ${NUM_VERSIONS}"
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
                \"content\": $(echo "${schema}" | jq -c .)
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
                \"content\": $(echo "${schema}" | jq -c .)
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

# Create artifacts with labels
echo ""
echo "Creating ${NUM_ARTIFACTS} artifacts with ${NUM_LABELS} labels each..."
artifacts_per_group=$((NUM_ARTIFACTS / NUM_GROUPS))
artifact_count=0

for ((g=1; g<=NUM_GROUPS; g++)); do
    group_id="benchmark-group-${g}"

    for ((a=1; a<=artifacts_per_group; a++)); do
        artifact_count=$((artifact_count + 1))

        # Generate artifact details
        domain="${DOMAINS[$((RANDOM % ${#DOMAINS[@]}))]}"
        artifact_id="${domain}-service-api-${artifact_count}"
        name="${domain^} Service API ${artifact_count}"
        description="API schema for ${domain} service - artifact ${artifact_count} with search optimization testing"

        # Generate labels
        labels=$(generate_labels ${NUM_LABELS})

        # Create artifact
        create_artifact "${group_id}" "${artifact_id}" "${name}" "${description}" "${labels}"

        # Create additional versions
        for ((v=2; v<=NUM_VERSIONS; v++)); do
            version="${v}.0.0"
            create_version "${group_id}" "${artifact_id}" "${version}" "{}"
        done

        printf "\rArtifacts created: %d/%d" $artifact_count $NUM_ARTIFACTS
    done
done
echo ""

echo ""
echo "=============================================="
echo "Data seeding complete!"
echo "Created: ${NUM_GROUPS} groups"
echo "Created: ${artifact_count} artifacts"
echo "Created: $((artifact_count * NUM_VERSIONS)) total versions"
echo "=============================================="
