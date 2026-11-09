#!/bin/bash

# Default load test configuration with ramp-up, sustained, and ramp-down phases
RAMP_UP_DURATION=60      # Ramp-up phase duration in seconds
SUSTAINED_DURATION=300   # Sustained load duration in seconds
RAMP_DOWN_DURATION=60    # Ramp-down phase duration in seconds
MIN_RPS=1                # Minimum requests per second (start/end rate)
MAX_RPS=10               # Maximum requests per second (peak rate)
REGISTRY_URL="http://localhost:8080"

# Display usage information
usage() {
  cat << EOF
Usage: $0 [OPTIONS]

This script performs a load test on the Apicurio Registry with ramp-up, sustained, and ramp-down phases.

Options:
  -u, --url URL              Registry URL (default: http://localhost:8080)
  -r, --ramp-up SECONDS      Ramp-up phase duration in seconds (default: 60)
  -s, --sustained SECONDS    Sustained load phase duration in seconds (default: 300)
  -d, --ramp-down SECONDS    Ramp-down phase duration in seconds (default: 60)
  -m, --min-rps NUMBER       Minimum requests per second (default: 1)
  -M, --max-rps NUMBER       Maximum requests per second (default: 10)
  -h, --help                 Display this help message

Examples:
  # Run with default settings
  $0

  # Run with custom URL and higher load
  $0 --url http://myregistry:8080 --max-rps 50

  # Quick test with shorter durations
  $0 -r 30 -s 60 -d 30 -M 20

EOF
  exit 0
}

# Parse command-line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    -u|--url)
      REGISTRY_URL="$2"
      shift 2
      ;;
    -r|--ramp-up)
      RAMP_UP_DURATION="$2"
      shift 2
      ;;
    -s|--sustained)
      SUSTAINED_DURATION="$2"
      shift 2
      ;;
    -d|--ramp-down)
      RAMP_DOWN_DURATION="$2"
      shift 2
      ;;
    -m|--min-rps)
      MIN_RPS="$2"
      shift 2
      ;;
    -M|--max-rps)
      MAX_RPS="$2"
      shift 2
      ;;
    -h|--help)
      usage
      ;;
    *)
      echo "Error: Unknown option: $1" >&2
      echo "Use -h or --help for usage information" >&2
      exit 1
      ;;
  esac
done

# Validate parameters
if [ "$RAMP_UP_DURATION" -lt 0 ] || [ "$SUSTAINED_DURATION" -lt 0 ] || [ "$RAMP_DOWN_DURATION" -lt 0 ]; then
  echo "Error: Duration values must be non-negative" >&2
  exit 1
fi

if [ "$MIN_RPS" -lt 1 ] || [ "$MAX_RPS" -lt 1 ]; then
  echo "Error: RPS values must be at least 1" >&2
  exit 1
fi

if [ "$MIN_RPS" -gt "$MAX_RPS" ]; then
  echo "Error: MIN_RPS ($MIN_RPS) cannot be greater than MAX_RPS ($MAX_RPS)" >&2
  exit 1
fi

export REGISTRY_URL

# Global counter for unique schema names
SCHEMA_COUNTER=0
COUNTER_LOCK="/tmp/metrics_test_counter_$$"

get_next_index() {
  (
    flock -x 200
    SCHEMA_COUNTER=$((SCHEMA_COUNTER + 1))
    echo "$SCHEMA_COUNTER"
  ) 200>"$COUNTER_LOCK"
}

register_schema() {
  INDEX=$(get_next_index)

  SUBJECT="test-schema-$INDEX"

  URL="${REGISTRY_URL}/apis/ccompat/v7/subjects/${SUBJECT}/versions"

  SCHEMA=$(cat <<EOF
{
  "schema": "{\"type\": \"record\", \"name\": \"TestRecord$INDEX\", \"namespace\": \"com.example\", \"fields\": [{\"name\": \"id\", \"type\": \"string\"}, {\"name\": \"name\", \"type\": {\"type\": \"string\", \"avro.java.string\": \"String\"}}, {\"name\": \"email\", \"type\": {\"type\": \"string\", \"avro.java.string\": \"String\"}}, {\"name\": \"attributes\", \"type\": {\"type\": \"map\", \"values\": \"string\"}, \"default\": {}}]}"
}
EOF
)

echo "curl -s -o /dev/null -w %{http_code} -X POST -H Content-Type: application/json --data $SCHEMA $URL"

  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST -H "Content-Type: application/json" --data "$SCHEMA" "$URL")

  if [ "$HTTP_CODE" -lt 200 ] || [ "$HTTP_CODE" -ge 300 ]; then
    echo "ERROR [$HTTP_CODE] Failed to register subject: ${SUBJECT}" >&2
    return 1
  else
    echo "SUCCESS [$HTTP_CODE] Registered subject: ${SUBJECT}"
    return 0
  fi
}

export -f register_schema

# Calculate requests per second for a given elapsed time
calculate_rps() {
  local ELAPSED=$1
  local RPS

  if [ "$ELAPSED" -lt "$RAMP_UP_DURATION" ]; then
    # Ramp-up phase: linear increase from MIN_RPS to MAX_RPS
    local PROGRESS=$(echo "scale=4; $ELAPSED / $RAMP_UP_DURATION" | bc)
    RPS=$(echo "scale=2; $MIN_RPS + ($MAX_RPS - $MIN_RPS) * $PROGRESS" | bc)
    echo "ramp-up" "$RPS"
  elif [ "$ELAPSED" -lt $((RAMP_UP_DURATION + SUSTAINED_DURATION)) ]; then
    # Sustained phase: constant MAX_RPS
    echo "sustained" "$MAX_RPS"
  elif [ "$ELAPSED" -lt $((RAMP_UP_DURATION + SUSTAINED_DURATION + RAMP_DOWN_DURATION)) ]; then
    # Ramp-down phase: linear decrease from MAX_RPS to MIN_RPS
    local RAMP_DOWN_ELAPSED=$((ELAPSED - RAMP_UP_DURATION - SUSTAINED_DURATION))
    local PROGRESS=$(echo "scale=4; $RAMP_DOWN_ELAPSED / $RAMP_DOWN_DURATION" | bc)
    RPS=$(echo "scale=2; $MAX_RPS - ($MAX_RPS - $MIN_RPS) * $PROGRESS" | bc)
    echo "ramp-down" "$RPS"
  else
    echo "complete" "0"
  fi
}

run_load_test() {
  local START_TIME=$(date +%s)
  local TOTAL_REQUESTS=0
  local SUCCESSFUL_REQUESTS=0
  local FAILED_REQUESTS=0

  # Main load generation loop
  while true; do
    local CURRENT_TIME=$(date +%s)
    local ELAPSED=$((CURRENT_TIME - START_TIME))

    read PHASE RPS < <(calculate_rps "$ELAPSED")

    if [ "$PHASE" == "complete" ]; then
      echo ""
      echo "Load test complete!"
      break
    fi

    # Calculate delay between requests based on RPS
    local DELAY=$(echo "scale=4; 1 / $RPS" | bc)

    # Display status every 10 seconds
    if [ $((ELAPSED % 10)) -eq 0 ] && [ $((ELAPSED % 10)) -lt 1 ]; then
      echo "[${ELAPSED}s] Phase: $PHASE | Current RPS: $RPS | Total: $TOTAL_REQUESTS | Success: $SUCCESSFUL_REQUESTS | Failed: $FAILED_REQUESTS"
    fi

    # Send request
    register_schema &
    TOTAL_REQUESTS=$((TOTAL_REQUESTS + 1))

    # Wait for the calculated delay
    sleep "$DELAY"
  done

  # Clean up
  rm -f "$COUNTER_LOCK"

  echo ""
  echo "=========================================="
  echo "Load Test Summary"
  echo "=========================================="
  echo "Total requests: $TOTAL_REQUESTS"
  echo "Duration: $((RAMP_UP_DURATION + SUSTAINED_DURATION + RAMP_DOWN_DURATION))s"
  echo "=========================================="
}

echo "=========================================="
echo "Metrics Load Test with Ramp-Up/Ramp-Down"
echo "=========================================="
echo "Registry URL: $REGISTRY_URL"
echo "Ramp-up:   ${RAMP_UP_DURATION}s (${MIN_RPS} -> ${MAX_RPS} req/s)"
echo "Sustained: ${SUSTAINED_DURATION}s (${MAX_RPS} req/s)"
echo "Ramp-down: ${RAMP_DOWN_DURATION}s (${MAX_RPS} -> ${MIN_RPS} req/s)"
echo "Total duration: $((RAMP_UP_DURATION + SUSTAINED_DURATION + RAMP_DOWN_DURATION))s"
echo "=========================================="
echo ""

run_load_test
