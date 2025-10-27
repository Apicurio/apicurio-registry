#!/bin/bash

# Demo script for custom TOML artifact type in Apicurio Registry
# This script demonstrates the various capabilities of the custom artifact type

REGISTRY_URL="http://localhost:8080/apis/registry/v3"
GROUP_ID="example-group"
ARTIFACT_ID="app-config"

echo "=================================================="
echo "Custom TOML Artifact Type Demo"
echo "=================================================="
echo ""

# Step 1: List available artifact types
echo "1. Listing available artifact types..."
echo "   Command: curl -s $REGISTRY_URL/admin/config/artifactTypes"
curl -s "$REGISTRY_URL/admin/config/artifactTypes" | jq '.'
echo ""
echo "   Note: You should see TOML in the list above"
echo ""
read -p "Press Enter to continue..."
echo ""

# Step 2: Create a TOML artifact
echo "2. Creating a TOML artifact..."
TOML_CONTENT='[database]
host = "localhost"
port = 5432
database = "myapp"

[server]
host = "0.0.0.0"
port = 8080
debug = true'

echo "   TOML Content:"
echo "$TOML_CONTENT"
echo ""

CREATE_RESPONSE=$(curl -s -X POST "$REGISTRY_URL/groups/$GROUP_ID/artifacts" \
  -H "Content-Type: application/json" \
  -H "X-Registry-ArtifactId: $ARTIFACT_ID" \
  -H "X-Registry-ArtifactType: TOML" \
  -d "{
    \"artifactId\": \"$ARTIFACT_ID\",
    \"artifactType\": \"TOML\",
    \"firstVersion\": {
      \"version\": \"1.0.0\",
      \"content\": {
        \"content\": $(echo "$TOML_CONTENT" | jq -Rs .),
        \"contentType\": \"application/toml\"
      }
    }
  }")

echo "   Response:"
echo "$CREATE_RESPONSE" | jq '.'
echo ""
read -p "Press Enter to continue..."
echo ""

# Step 3: Get the artifact content
echo "3. Retrieving the artifact content..."
echo "   Command: curl -s $REGISTRY_URL/groups/$GROUP_ID/artifacts/$ARTIFACT_ID/versions/1.0.0/content"
curl -s "$REGISTRY_URL/groups/$GROUP_ID/artifacts/$ARTIFACT_ID/versions/1.0.0/content"
echo ""
echo ""
read -p "Press Enter to continue..."
echo ""

# Step 4: Test content auto-detection (ContentAccepter)
echo "4. Creating artifact without specifying type (auto-detection)..."
AUTO_ARTIFACT_ID="auto-detected-config"
AUTO_RESPONSE=$(curl -s -X POST "$REGISTRY_URL/groups/$GROUP_ID/artifacts" \
  -H "Content-Type: application/json" \
  -H "X-Registry-ArtifactId: $AUTO_ARTIFACT_ID" \
  -d "{
    \"artifactId\": \"$AUTO_ARTIFACT_ID\",
    \"firstVersion\": {
      \"version\": \"1.0.0\",
      \"content\": {
        \"content\": $(echo "$TOML_CONTENT" | jq -Rs .),
        \"contentType\": \"application/toml\"
      }
    }
  }")

echo "   Response (note the artifactType field):"
echo "$AUTO_RESPONSE" | jq '.'
echo ""
read -p "Press Enter to continue..."
echo ""

# Step 5: Create a new version with compatibility check
echo "5. Creating a new version (compatible - adds new section)..."
TOML_V2_CONTENT='[database]
host = "localhost"
port = 5432
database = "myapp"

[server]
host = "0.0.0.0"
port = 8080
debug = false

[cache]
enabled = true
ttl = 3600'

echo "   New TOML Content (added [cache] section):"
echo "$TOML_V2_CONTENT"
echo ""

V2_RESPONSE=$(curl -s -X POST "$REGISTRY_URL/groups/$GROUP_ID/artifacts/$ARTIFACT_ID/versions" \
  -H "Content-Type: application/json" \
  -d "{
    \"version\": \"2.0.0\",
    \"content\": {
      \"content\": $(echo "$TOML_V2_CONTENT" | jq -Rs .),
      \"contentType\": \"application/toml\"
    }
  }")

echo "   Response:"
echo "$V2_RESPONSE" | jq '.'
echo ""
read -p "Press Enter to continue..."
echo ""

# Step 6: Enable compatibility rule and test
echo "6. Enabling compatibility rule (BACKWARD)..."
RULE_RESPONSE=$(curl -s -X POST "$REGISTRY_URL/groups/$GROUP_ID/rules" \
  -H "Content-Type: application/json" \
  -d '{
    "ruleType": "COMPATIBILITY",
    "config": "BACKWARD"
  }')

echo "   Compatibility rule enabled."
echo ""

echo "   Attempting to create incompatible version (removes [database] section)..."
INCOMPATIBLE_CONTENT='[server]
host = "0.0.0.0"
port = 9090
debug = false'

INCOMPATIBLE_RESPONSE=$(curl -s -X POST "$REGISTRY_URL/groups/$GROUP_ID/artifacts/$ARTIFACT_ID/versions" \
  -H "Content-Type: application/json" \
  -d "{
    \"version\": \"3.0.0\",
    \"content\": {
      \"content\": $(echo "$INCOMPATIBLE_CONTENT" | jq -Rs .),
      \"contentType\": \"application/toml\"
    }
  }" || echo "Expected to fail")

echo "   Response (should show error):"
echo "$INCOMPATIBLE_RESPONSE" | jq '.'
echo ""
read -p "Press Enter to continue..."
echo ""

# Step 7: Test validation
echo "7. Testing content validation..."
echo "   Enabling validity rule (FULL)..."
VALIDITY_RULE_RESPONSE=$(curl -s -X POST "$REGISTRY_URL/groups/$GROUP_ID/rules" \
  -H "Content-Type: application/json" \
  -d '{
    "ruleType": "VALIDITY",
    "config": "FULL"
  }')

echo "   Validity rule enabled."
echo ""

echo "   Attempting to create artifact with invalid TOML..."
INVALID_TOML='This is not valid TOML
random text here
no structure'

INVALID_RESPONSE=$(curl -s -X POST "$REGISTRY_URL/groups/$GROUP_ID/artifacts" \
  -H "Content-Type: application/json" \
  -H "X-Registry-ArtifactId: invalid-toml" \
  -H "X-Registry-ArtifactType: TOML" \
  -d "{
    \"artifactId\": \"invalid-toml\",
    \"artifactType\": \"TOML\",
    \"firstVersion\": {
      \"version\": \"1.0.0\",
      \"content\": {
        \"content\": $(echo "$INVALID_TOML" | jq -Rs .),
        \"contentType\": \"application/toml\"
      }
    }
  }" 2>&1)

echo "   Response (should indicate validation failure):"
echo "$INVALID_RESPONSE" | jq '.' 2>/dev/null || echo "$INVALID_RESPONSE"
echo ""
read -p "Press Enter to continue..."
echo ""

# Step 8: List all versions
echo "8. Listing all versions of the artifact..."
echo "   Command: curl -s $REGISTRY_URL/groups/$GROUP_ID/artifacts/$ARTIFACT_ID/versions"
curl -s "$REGISTRY_URL/groups/$GROUP_ID/artifacts/$ARTIFACT_ID/versions" | jq '.'
echo ""
read -p "Press Enter to continue..."
echo ""

# Summary
echo "=================================================="
echo "Demo Complete!"
echo "=================================================="
echo ""
echo "This demo demonstrated:"
echo "  ✓ Custom artifact type registration (TOML)"
echo "  ✓ Artifact creation with explicit type"
echo "  ✓ Content auto-detection (ContentAccepter)"
echo "  ✓ Compatibility checking"
echo "  ✓ Content validation"
echo "  ✓ Version management"
echo ""
echo "You can explore the registry using:"
echo "  - Web UI:  http://localhost:8888"
echo "  - API:     http://localhost:8080/apis/registry/v3"
echo ""