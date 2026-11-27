#!/usr/bin/env bash
# Counts the SQL statements and transactions that Apicurio Registry runs against PostgreSQL for
# common write requests (issue #11412). See README.md in this directory for how to run it.
#
# Requirements: podman (or docker via CONTAINER_CLI), curl, jq, a JDK, and a registry build:
#   ./mvnw -q -B -ntp package -pl app -DskipTests -Dcheckstyle.skip
#
# Output: one markdown file per run in OUT_DIR (default: target/storage-write-cost).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CLI="${CONTAINER_CLI:-podman}"
PG_NAME="${PG_NAME:-pg-write-cost}"
PG_PORT="${PG_PORT:-55433}"
REG_PORT="${REG_PORT:-18080}"
MCP_PORT="${MCP_PORT:-18099}"
CREATES="${CREATES:-100}"
TOOLS="${TOOLS:-500}"
LABEL="${LABEL:-run}"
OUT_DIR="${OUT_DIR:-$ROOT/perf-tests/target/storage-write-cost}"
APP="$ROOT/app/target/quarkus-app/quarkus-run.jar"
API="http://localhost:$REG_PORT/apis/registry/v3"
WORK="$(mktemp -d)"
mkdir -p "$OUT_DIR"
OUT="$OUT_DIR/$LABEL.md"

[ -f "$APP" ] || { echo "Missing $APP; build the app module first." >&2; exit 1; }

REG_PID=""
MCP_PID=""
cleanup() {
    [ -n "$REG_PID" ] && kill "$REG_PID" 2>/dev/null || true
    [ -n "$MCP_PID" ] && kill "$MCP_PID" 2>/dev/null || true
    "$CLI" rm -f "$PG_NAME" >/dev/null 2>&1 || true
    rm -rf "$WORK"
}
trap cleanup EXIT

# Statistics are read as a separate role connected to the "postgres" database, so neither the
# metering queries nor their transactions are counted against the registry database.
psql_meter() {
    "$CLI" exec -i "$PG_NAME" psql -U meter -d postgres -At -F '|' -v ON_ERROR_STOP=1 -c "$1"
}

echo "Starting PostgreSQL 16 with pg_stat_statements..."
"$CLI" rm -f "$PG_NAME" >/dev/null 2>&1 || true
"$CLI" run -d --name "$PG_NAME" -p "$PG_PORT:5432" -e POSTGRES_USER=registry -e POSTGRES_PASSWORD=registry \
    -e POSTGRES_DB=registry docker.io/library/postgres:16 \
    postgres -c shared_preload_libraries=pg_stat_statements -c pg_stat_statements.track=all \
    -c pg_stat_statements.max=10000 -c track_counts=on >/dev/null
for _ in $(seq 1 60); do
    "$CLI" exec "$PG_NAME" pg_isready -U registry -d registry >/dev/null 2>&1 && break
    sleep 1
done
sleep 2
"$CLI" exec -i "$PG_NAME" psql -U registry -d postgres -v ON_ERROR_STOP=1 -q <<'SQL'
CREATE EXTENSION pg_stat_statements;
CREATE ROLE meter LOGIN SUPERUSER;
SQL

echo "Compiling the MCP tools stub..."
mkdir -p "$WORK/stub"
JACKSON_CP="$(ls "$ROOT"/app/target/quarkus-app/lib/main/com.fasterxml.jackson.core.jackson-*.jar | tr '\n' ':')"
cat > "$WORK/StubMain.java" <<'JAVA'
public class StubMain {
    public static void main(String[] args) throws Exception {
        new io.apicurio.registry.perftest.mcp.McpToolsStub(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        Thread.currentThread().join();
    }
}
JAVA
javac -d "$WORK/stub" -cp "$JACKSON_CP" \
    "$ROOT/perf-tests/src/main/java/io/apicurio/registry/perftest/mcp/McpToolsStub.java" "$WORK/StubMain.java"
java -cp "$WORK/stub:$JACKSON_CP" StubMain "$MCP_PORT" "$TOOLS" > "$WORK/stub.log" 2>&1 &
MCP_PID=$!

echo "Starting the registry..."
APICURIO_STORAGE_KIND=sql APICURIO_STORAGE_SQL_KIND=postgresql \
APICURIO_DATASOURCE_URL="jdbc:postgresql://localhost:$PG_PORT/registry" \
APICURIO_DATASOURCE_USERNAME=registry APICURIO_DATASOURCE_PASSWORD=registry \
QUARKUS_HTTP_PORT="$REG_PORT" \
APICURIO_FEATURES_EXPERIMENTAL_ENABLED=true APICURIO_AGENTS_ENABLED=true APICURIO_MCP_IMPORT_ENABLED=true \
APICURIO_MCP_IMPORT_ALLOWED_HOSTS=localhost APICURIO_MCP_IMPORT_LOOPBACK_ENABLED=true \
APICURIO_MCP_IMPORT_INSECURE_HTTP_ENABLED=true \
    java -jar "$APP" > "$OUT_DIR/$LABEL-registry.log" 2>&1 &
REG_PID=$!
for _ in $(seq 1 120); do
    curl -sf "$API/system/info" >/dev/null 2>&1 && break
    sleep 1
done
curl -sf "$API/system/info" >/dev/null || { echo "Registry did not start" >&2; exit 1; }
# Let start-up work (schema checks, first scheduled jobs) settle before measuring.
sleep 10

now_ms() { python3 -c 'import time; print(int(time.time()*1000))'; }

xact_commit() {
    psql_meter "SELECT xact_commit FROM pg_stat_database WHERE datname = 'registry'"
}

reset_stats() {
    psql_meter "SELECT pg_stat_statements_reset()" >/dev/null
    # Idle backends flush pg_stat_database counters at most every 10 s (PGSTAT_IDLE_INTERVAL).
    sleep 11
    XACT_BEFORE="$(xact_commit)"
}

# Records the statement and transaction counts since reset_stats under the given title.
report() {
    local title="$1" wall_ms="$2" divisor="${3:-1}"
    # Idle backends flush their transaction counters at most every 10 s (PGSTAT_IDLE_INTERVAL).
    sleep 11
    local xact_after total
    xact_after="$(xact_commit)"
    total="$(psql_meter "SELECT coalesce(sum(s.calls), 0) FROM pg_stat_statements s
        JOIN pg_database d ON d.oid = s.dbid WHERE d.datname = 'registry'")"
    {
        echo "### $title"
        echo
        echo "- wall time: ${wall_ms} ms (divisor $divisor: $(( wall_ms / divisor )) ms each)"
        echo "- statements (all calls, incl. BEGIN/COMMIT/SAVEPOINT): $total (per unit: $(echo "scale=1; $total / $divisor" | bc))"
        echo "- xact_commit delta: $(( xact_after - XACT_BEFORE )) (per unit: $(echo "scale=1; ($xact_after - $XACT_BEFORE) / $divisor" | bc))"
        echo
        echo "| calls | per unit | statement |"
        echo "| ---: | ---: | --- |"
        psql_meter "SELECT s.calls, round(s.calls::numeric / $divisor, 2),
                replace(regexp_replace(left(s.query, 140), '\s+', ' ', 'g'), '|', '\|')
            FROM pg_stat_statements s JOIN pg_database d ON d.oid = s.dbid
            WHERE d.datname = 'registry' ORDER BY s.calls DESC, s.query" \
            | awk -F '|' '{ q = $3; for (i = 4; i <= NF; i++) q = q "|" $i; printf "| %s | %s | `%s` |\n", $1, $2, q }'
        echo
    } >> "$OUT"
}

avro() {
    printf '{"type":"record","name":"R%s","fields":[{"name":"f","type":"string"}]}' "$1" | jq -Rs .
}

create_body() {
    printf '{"artifactId":"%s","artifactType":"AVRO","firstVersion":{"content":{"contentType":"application/json","content":%s}}}' \
        "$1" "$(avro "$2")"
}

post() {
    local url="$1" body="$2" code
    code="$(curl -s -o "$WORK/resp.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
        --data "$body" "$url")"
    [ "$code" = "200" ] || [ "$code" = "202" ] || { echo "POST $url -> $code: $(cat "$WORK/resp.json")" >&2; exit 1; }
}

{
    echo "# Storage write cost: $LABEL"
    echo
    echo "Commit: $(git -C "$ROOT" rev-parse --short HEAD), date: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo
} > "$OUT"

echo "Idle baseline (10 s)..."
reset_stats
sleep 10
report "Idle baseline: 10 s with no requests (background jobs)" 10000

echo "Scenario (a): one artifact create, new group..."
reset_stats
start=$(now_ms)
post "$API/groups/g-a/artifacts" "$(create_body a1 a1)"
report "(a) Create artifact with first version, new group" $(( $(now_ms) - start ))

echo "Scenario (b): one new version of an existing artifact..."
reset_stats
start=$(now_ms)
post "$API/groups/g-a/artifacts/a1/versions" "{\"content\":{\"contentType\":\"application/json\",\"content\":$(avro a1v2)}}"
report "(b) Create a new version of an existing artifact" $(( $(now_ms) - start ))

echo "Scenario (c): $CREATES sequential creates in an existing group..."
reset_stats
start=$(now_ms)
for i in $(seq 1 "$CREATES"); do
    post "$API/groups/g-a/artifacts" "$(create_body "c$i" "c$i")"
done
report "(c) $CREATES sequential artifact creates, existing group (per create)" $(( $(now_ms) - start )) "$CREATES"

echo "Scenario (d): async MCP import of $TOOLS tools..."
post "$API/groups/g-mcp/artifacts" "{\"artifactId\":\"server\",\"artifactType\":\"MCP_SERVER\",\"firstVersion\":{\"content\":{\"contentType\":\"application/json\",\"content\":$(echo '{"name":"io.github.perf/server","description":"Perf","version":"1.0.0"}' | jq -Rs .)}}}"
reset_stats
start=$(now_ms)
location="$(curl -s -D - -o "$WORK/resp.json" -X POST -H 'Content-Type: application/json' \
    --data "{\"url\":\"http://localhost:$MCP_PORT/mcp\",\"async\":true}" \
    "$API/groups/g-mcp/artifacts/server/mcp-import" | awk 'tolower($1) == "location:" { print $2 }' | tr -d '\r')"
[ -n "$location" ] || { echo "MCP import did not start: $(cat "$WORK/resp.json")" >&2; exit 1; }
case "$location" in http*) ;; *) location="http://localhost:$REG_PORT$location" ;; esac
state=RUNNING
while [ "$state" = "RUNNING" ] || [ "$state" = "PENDING" ]; do
    sleep 0.2
    state="$(curl -s "$location" | jq -r .state)"
done
elapsed=$(( $(now_ms) - start ))
[ "$state" = "COMPLETED" ] || { echo "MCP import ended in state $state: $(curl -s "$location")" >&2; exit 1; }
report "(d) Async MCP import of $TOOLS tools (to COMPLETED; per tool)" "$elapsed" "$TOOLS"

echo "Done: $OUT"
