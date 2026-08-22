#!/bin/bash
set -euo pipefail

IMAGE=${1:-}

if [ -z "$IMAGE" ]; then
  echo "Usage: $0 <image-name>"
  exit 1
fi

echo "Running smoke tests for image: $IMAGE"

# 1. Verify clean exit on missing config
echo "Verifying clean exit on missing config..."
set +e
OUTPUT=$(timeout 60 docker run --rm \
  -e APICURIO_STORAGE_KIND=sql \
  -e APICURIO_DATASOURCE_URL="jdbc:postgresql://192.0.2.1:5432/nonexistent" \
  -e APICURIO_DATASOURCE_USERNAME="test" \
  -e APICURIO_DATASOURCE_PASSWORD="test" \
  "$IMAGE" 2>&1)
EXIT_CODE=$?
set -e

if [ $EXIT_CODE -eq 0 ]; then
  echo "ERROR: Container should have failed on missing config!"
  exit 1
fi
if [ $EXIT_CODE -eq 124 ]; then
  echo "ERROR: Container hung and was killed by timeout!"
  exit 1
fi
echo "OK: Clean error on missing config found (exit code $EXIT_CODE)."

# 2. Start container for dynamic checks
echo "Starting container..."
CID=$(docker run -P -d "$IMAGE")

# Ensure cleanup on exit
trap 'docker rm -f $CID' EXIT

if [ "$(docker inspect -f '{{.State.Running}}' "$CID")" != "true" ]; then
  echo "ERROR: Container failed to start!"
  docker logs "$CID"
  exit 1
fi

# Find the mapped port for 8080 on the host
HOST_PORT=$(docker port "$CID" 8080 | awk -F ':' '{print $NF}' | head -n 1)
if [ -z "$HOST_PORT" ]; then
  echo "ERROR: Port 8080 is not exposed!"
  exit 1
fi

# Wait for container to be ready by polling from the host
echo "Waiting for container to start (polling health endpoint on port $HOST_PORT)..."
READY=false
for i in {1..30}; do
  if curl -fsS "http://localhost:$HOST_PORT/health/ready" > /dev/null 2>&1; then
    READY=true
    break
  fi
  sleep 2
done

if [ "$READY" != "true" ]; then
  echo "ERROR: Container failed to become ready!"
  docker logs "$CID"
  exit 1
fi
echo "OK: Container is ready."

# 3. Verify non-root user
echo "Verifying non-root user..."
USER_ID=$(docker exec "$CID" id -u)
if [ "$USER_ID" -eq 0 ]; then
  echo "ERROR: Container is running as root (uid 0)"
  exit 1
fi
echo "OK: Running as user ID $USER_ID"

# 4. Verify expected ports
echo "Verifying exposed ports..."
# Extract exposed ports using docker port. Note that this checks EXPOSE declarations, not actual listening ports.
PORTS=$(docker port "$CID" | awk -F'/' '{print $1}' | sort -u)
echo "Exposed ports:"
echo "$PORTS"

if [ -z "$PORTS" ]; then
  echo "ERROR: No ports are exposed. Expected at least 8080."
  exit 1
fi

# We allow 8080 (http), 8443 (https), 9000 (metrics/management if exposed by default in Dockerfile)
ALLOWED_PORTS="8080 8443 9000"

for port in $PORTS; do
  if ! echo "$ALLOWED_PORTS" | grep -qw "$port"; then
    echo "ERROR: Unexpected port $port is exposed!"
    exit 1
  fi
done

if ! echo "$PORTS" | grep -qw "8080"; then
  echo "ERROR: Port 8080 is not exposed."
  exit 1
fi

echo "OK: Ports verified."

echo "Smoke tests passed successfully."
