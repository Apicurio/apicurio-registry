#!/usr/bin/env bash
# Builds the Registry MCP server image from this checkout, to run the example against local changes:
#   ./build.sh && MCP_SERVER_IMAGE=apicurio/apicurio-registry-mcp-server:local docker compose up -d
# Not needed in CI, which sets MCP_SERVER_IMAGE to the image built for the pull request.
set -euo pipefail
cd "$(dirname "$0")/../.."

TAG=${MCP_SERVER_LOCAL_TAG:-apicurio/apicurio-registry-mcp-server:local}
./mvnw install -Pfull -pl mcp -am -DskipTests
docker build -f mcp/target/docker/Dockerfile.jvm -t "$TAG" mcp/target/docker
echo "Built $TAG"
