#!/bin/bash

# Script to populate Confluent Schema Registry with test schemas
# Usage: ./populate-confluent-registry.sh [schema-registry-url]

SCHEMA_REGISTRY_URL="${1:-http://localhost:8081}"

echo "Populating Confluent Schema Registry at: $SCHEMA_REGISTRY_URL"

# Function to register a schema
register_schema() {
    local subject=$1
    local schema=$2
    local schema_type=${3:-AVRO}

    echo "Registering schema for subject: $subject"

    curl -X POST "$SCHEMA_REGISTRY_URL/subjects/$subject/versions" \
         -H "Content-Type: application/vnd.schemaregistry.v1+json" \
         -d "{\"schemaType\":\"$schema_type\",\"schema\":\"$schema\"}" \
         -s -o /dev/null -w "Response code: %{http_code}\n"
}

# Register 10 AVRO schemas
for i in {1..10}; do
    SUBJECT="test-subject-$i"
    SCHEMA="{\\\"type\\\":\\\"record\\\",\\\"name\\\":\\\"TestRecord$i\\\",\\\"fields\\\":[{\\\"name\\\":\\\"field1\\\",\\\"type\\\":\\\"string\\\"},{\\\"name\\\":\\\"field2\\\",\\\"type\\\":\\\"int\\\"}]}"
    register_schema "$SUBJECT" "$SCHEMA" "AVRO"
done

# Register a JSON schema
JSON_SUBJECT="test-json-schema"
JSON_SCHEMA="{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"name\\\":{\\\"type\\\":\\\"string\\\"},\\\"age\\\":{\\\"type\\\":\\\"integer\\\"}}}"
register_schema "$JSON_SUBJECT" "$JSON_SCHEMA" "JSON"

# Register a Protobuf schema
PROTO_SUBJECT="test-proto-schema"
PROTO_SCHEMA="syntax = \\\"proto3\\\";\\nmessage TestMessage {\\n  string name = 1;\\n  int32 age = 2;\\n}"
register_schema "$PROTO_SUBJECT" "$PROTO_SCHEMA" "PROTOBUF"

# Set compatibility mode for a subject
echo "Setting compatibility mode for test-subject-1"
curl -X PUT "$SCHEMA_REGISTRY_URL/config/test-subject-1" \
     -H "Content-Type: application/vnd.schemaregistry.v1+json" \
     -d '{"compatibility":"BACKWARD"}' \
     -s -o /dev/null -w "Response code: %{http_code}\n"

# Set global compatibility
echo "Setting global compatibility mode"
curl -X PUT "$SCHEMA_REGISTRY_URL/config" \
     -H "Content-Type: application/vnd.schemaregistry.v1+json" \
     -d '{"compatibility":"BACKWARD"}' \
     -s -o /dev/null -w "Response code: %{http_code}\n"

echo ""
echo "Schema registration complete!"
echo ""
echo "Listing all subjects:"
curl -X GET "$SCHEMA_REGISTRY_URL/subjects" -s | jq '.'

echo ""
echo "You can now export the data using the Confluent Export Utility"