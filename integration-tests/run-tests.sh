#!/bin/bash

# Function to display usage information
show_usage() {
    echo "Usage: $0 [--testGroups <profile>] [--registryProtocol <protocol>] [--registryHost <host>] [--registryPort <port>] [--realmName <realm>]"
    echo ""
    echo "This script runs the apicurio-registry integration tests against a deployed Registry instance."
    echo ""
    echo "Arguments:"
    echo "  --testGroups       Optional. Test group(s) to run (default: smoke | serdes | acceptance). Examples: smoke, auth, 'smoke | serdes'"
    echo "  --registryProtocol  Optional. Registry protocol (default: http)"
    echo "  --registryHost      Optional. Registry host (default: localhost)"
    echo "  --registryPort      Optional. Registry port (default: 8080)"
    echo "  --authUrl           Optional. Keycloak token auth URL (default: http://localhost:8081/realms/registry/protocol/openid-connect/token)"
}

# Parse command line arguments
TEST_GROUPS=""
REGISTRY_PROTOCOL=""
REGISTRY_HOST=""
REGISTRY_PORT=""
TOKEN_AUTH_URL=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --testGroups)
            TEST_GROUPS="$2"
            shift 2
            ;;
        --registryProtocol)
            REGISTRY_PROTOCOL="$2"
            shift 2
            ;;
        --registryHost)
            REGISTRY_HOST="$2"
            shift 2
            ;;
        --registryPort)
            REGISTRY_PORT="$2"
            shift 2
            ;;
        --authUrl)
            TOKEN_AUTH_URL="$2"
            shift 2
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# If no test profile specified, use the default groups from the pom.xml
# (smoke | serdes | acceptance)

if [ -z "$REGISTRY_PROTOCOL" ]; then
    REGISTRY_PROTOCOL=http
fi

if [ -z "$REGISTRY_HOST" ]; then
    REGISTRY_HOST=localhost
fi

if [ -z "$REGISTRY_PORT" ]; then
    REGISTRY_PORT=8080
fi

if [ -z "$TOKEN_AUTH_URL" ]; then
    TOKEN_AUTH_URL=http://localhost:8081/realms/registry/protocol/openid-connect/token
fi

# Get the directory where this script is located
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$BASE_DIR/.."
REGISTRY_URL="$REGISTRY_PROTOCOL://$REGISTRY_HOST:$REGISTRY_PORT"

cd $PROJECT_DIR

# Display some diagnostic info
echo ""
echo "Registry System Info:"
echo "--"
curl -s $REGISTRY_URL/apis/registry/v3/system/info | jq
echo "--"
echo ""
echo "------------------------------------"
echo "Running Integration Tests (groups: ${TEST_GROUPS:-default})..."
echo "------------------------------------"

# Build the mvnw command arguments
MVNW_ARGS=(verify -am --no-transfer-progress -Pintegration-tests)
if [ -n "$TEST_GROUPS" ]; then
    MVNW_ARGS+=("-Dgroups=$TEST_GROUPS")
fi

# Run the integration tests
./mvnw "${MVNW_ARGS[@]}" \
    -pl integration-tests \
    -Dmaven.javadoc.skip=true \
    -Dquarkus.oidc.token-path=$TOKEN_AUTH_URL \
    -Dquarkus.http.test-protocol=$REGISTRY_PROTOCOL \
    -Dquarkus.http.test-host=$REGISTRY_HOST \
    -Dquarkus.http.test-port=$REGISTRY_PORT

# Check if the mvnw command succeeded
MVNW_EXIT_CODE=$?
if [ $MVNW_EXIT_CODE -ne 0 ]; then
    echo ""
    echo "Integration tests failed with exit code: $MVNW_EXIT_CODE"
    exit $MVNW_EXIT_CODE
fi

echo ""
echo "Integration tests completed successfully."
