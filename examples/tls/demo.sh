#!/usr/bin/env bash
# Checks that the registries in this example serve TLS, and that every client verifies it: curl, the
# Java SDK, the Kafka serdes and the Maven plugin, with and without client certificates.
# Usage: ./demo.sh   (requires Docker Compose, curl, jq, openssl and JDK 21 or later)
# Generates the certificates (certs/generate-certs.sh) and starts the containers if needed, so it is
# the only command to run. Prints one ok/FAIL line per check, ends with "All checks passed." and
# exits non-zero on failure. Safe to run again: it starts by deleting the groups it creates.
set -euo pipefail
cd "$(dirname "$0")"

REGISTRY_PORT=${REGISTRY_PORT:-8443}
REGISTRY_HTTP_PORT=${REGISTRY_HTTP_PORT:-8080}
MTLS_PORT=${MTLS_PORT:-9443}
KAFKA_PORT=${KAFKA_PORT:-9092}
REGISTRY=${REGISTRY:-https://localhost:$REGISTRY_PORT}
REGISTRY_IP=${REGISTRY_IP:-https://127.0.0.1:$REGISTRY_PORT}
REGISTRY_HTTP=${REGISTRY_HTTP:-http://localhost:$REGISTRY_HTTP_PORT}
MTLS=${MTLS:-https://localhost:$MTLS_PORT}
KAFKA=${KAFKA:-localhost:$KAFKA_PORT}
V3=/apis/registry/v3
C=certs
failures=0

ok()   { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
part() { printf '\n%s\n' "$1"; }
check() { if [[ "$3" == "$2" ]]; then ok "$1"; else fail "$1: got '$3', expected '$2'"; fi; }

# code <url> [curl args...]: the HTTP status of a GET, or 000 when there is no HTTP answer
code() { local url=$1; shift; curl -s --max-time 30 -o /dev/null -w '%{http_code}' "$url" "$@" || true; }
# curl_exit <url> [curl args...]: curl's exit code (0 is success; 60 is "the certificate is not trusted")
curl_exit() { local url=$1; shift; local rc=0; curl -s --max-time 30 -o /dev/null "$url" "$@" || rc=$?; echo "$rc"; }
CA=(--cacert "$C/ca-cert.pem")
DEV=(-u developer:developer)
CLIENT=(--cert "$C/client-cert.pem" --key "$C/client-key.pem")

# ------------------------------------------------------------------------------ setup
if [ ! -f "$C/ca-cert.pem" ]; then
  if docker compose ps -q 2> /dev/null | grep -q .; then
    echo "certs/ is empty but containers are running: run 'docker compose down -v' first" >&2
    exit 1
  fi
  echo "Generating the certificates (certs/generate-certs.sh)..."
  ./certs/generate-certs.sh > /dev/null
fi

echo "Starting the containers (docker compose up -d)..."
for attempt in 1 2 3; do
  if docker compose up -d --pull missing > /dev/null 2>&1; then break; fi
  [ "$attempt" = 3 ] && { docker compose up -d; exit 1; }
  sleep 20
done

wait_for() {
  local deadline=$((SECONDS + ${WAIT_TIMEOUT:-300}))
  until [ "$(code "$2" "${@:3}")" = 200 ]; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "Timed out waiting for $1 at $2 (see 'docker compose logs')" >&2
      exit 1
    fi
    sleep 2
  done
}
echo "Waiting for the registries..."
wait_for registry "$REGISTRY$V3/system/info" "${CA[@]}"
wait_for mtls-proxy "$MTLS$V3/system/info" "${CA[@]}" "${CLIENT[@]}"

if [ ! -d clients/target/classes ] || [ ! -d ../../utils/maven-plugin/target/classes ]; then
  echo "Building the clients and the Maven plugin (first run only, a few minutes)..."
  log=$(mktemp)
  ./build.sh > "$log" 2>&1 || { tail -40 "$log"; echo "build.sh failed; full log: $log" >&2; exit 1; }
fi

echo "Resetting the example data..."
curl -s -o /dev/null -X DELETE "$REGISTRY$V3/groups/maven-tls" "${CA[@]}" "${DEV[@]}" || true
for g in maven-tls sdk-tls default; do
  curl -s -o /dev/null -X DELETE "$MTLS$V3/groups/$g" "${CA[@]}" "${CLIENT[@]}" || true
done
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:29092 --delete \
  --if-exists --topic tls-greetings > /dev/null 2>&1 || true

# ------------------------------------------------------------------------------ step 3
part "Step 3: registry serves HTTPS with the example's certificate"
check "with the example CA (--cacert) and credentials: 200" 200 "$(code "$REGISTRY$V3/system/info" "${CA[@]}" "${DEV[@]}")"
check "the response is the registry's (system info)" "Apicurio Registry (SQL)" \
  "$(curl -s "$REGISTRY$V3/system/info" "${CA[@]}" "${DEV[@]}" | jq -r .name)"
issuer=$(openssl s_client -connect "localhost:$REGISTRY_PORT" -servername localhost < /dev/null 2> /dev/null \
  | openssl x509 -noout -issuer 2> /dev/null || true)
check "the certificate it presents is issued by the example CA" yes \
  "$(grep -q 'Apicurio Registry Example CA' <<< "$issuer" && echo yes || echo "no ($issuer)")"
check "TLS fine, no credentials: 401" 401 "$(code "$REGISTRY$V3/groups" "${CA[@]}")"
check "without the example CA, curl refuses the certificate (exit 60)" 60 "$(curl_exit "$REGISTRY$V3/system/info")"
check "with another CA (rogue-ca-cert.pem): refused (exit 60)" 60 \
  "$(curl_exit "$REGISTRY$V3/system/info" --cacert "$C/rogue-ca-cert.pem")"
check "by IP (127.0.0.1 is not in the certificate): hostname check fails (exit 60)" 60 \
  "$(curl_exit "$REGISTRY_IP$V3/system/info" "${CA[@]}")"

# ------------------------------------------------------------------------------ step 4
part "Step 4: plain HTTP: redirected (registry) or off (registry-mtls)"
check "registry, plain HTTP: 301" 301 "$(code "$REGISTRY_HTTP$V3/system/info")"
location=$(curl -s -o /dev/null -w '%{redirect_url}' "$REGISTRY_HTTP$V3/system/info" || true)
check "... to the HTTPS URL" "https://localhost:8443$V3/system/info" "$location"
check "registry-mtls, plain HTTP inside its container: connection refused (exit 7)" 7 \
  "$(docker compose exec -T registry-mtls sh -c 'curl -s -o /dev/null http://localhost:8080/apis/registry/v3/system/info; echo $?' | tr -d '\r')"
check "registry-mtls, HTTPS inside its container: 200" 200 \
  "$(docker compose exec -T registry-mtls curl -s -o /dev/null -w '%{http_code}' --cacert /certs/ca-cert.pem https://localhost:8443/apis/registry/v3/system/info)"
check "the management port (health, metrics) serves HTTPS too: 200" 200 \
  "$(docker compose exec -T registry curl -s -o /dev/null -w '%{http_code}' --cacert /certs/ca-cert.pem https://localhost:9000/health/ready)"

# ------------------------------------------------------------------------------ step 5
part "Step 5: mutual TLS (mtls-proxy -> registry-mtls)"
check "with a client certificate signed by the example CA: 200" 200 "$(code "$MTLS$V3/system/info" "${CA[@]}" "${CLIENT[@]}")"
check "without a client certificate: 400" 400 "$(code "$MTLS$V3/system/info" "${CA[@]}")"
check "... 'No required SSL certificate was sent'" yes \
  "$(curl -s "$MTLS$V3/system/info" "${CA[@]}" | grep -q 'No required SSL certificate was sent' && echo yes || echo no)"
check "with a client certificate from another CA: 400" 400 \
  "$(code "$MTLS$V3/system/info" "${CA[@]}" --cert "$C/rogue-client-cert.pem" --key "$C/rogue-client-key.pem")"
check "... 'The SSL certificate error'" yes \
  "$(curl -s "$MTLS$V3/system/info" "${CA[@]}" --cert "$C/rogue-client-cert.pem" --key "$C/rogue-client-key.pem" \
     | grep -q 'The SSL certificate error' && echo yes || echo no)"
check "the client verifies the proxy too: without the example CA, exit 60" 60 \
  "$(curl_exit "$MTLS$V3/system/info" "${CLIENT[@]}")"
check "registry-mtls publishes no port: only the proxy reaches it" none \
  "$(docker compose port registry-mtls 8443 2> /dev/null | grep -qE ':[1-9][0-9]*$' && echo published || echo none)"
check "with a client certificate: create a group" 200 \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$MTLS$V3/groups" "${CA[@]}" "${CLIENT[@]}" \
     -H 'Content-Type: application/json' -d '{"groupId": "sdk-tls"}')"

# ------------------------------------------------------------------------------ step 6
part "Step 6: the Maven plugin"
version=$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' clients/pom.xml | head -1)
plugin="io.apicurio:apicurio-registry-maven-plugin:$version"
maven_args=(-Dregistry.url="$REGISTRY$V3" -Dregistry.ip.url="$REGISTRY_IP$V3" -Dmtls.url="$MTLS$V3")
# mvn_run <goal@execution>: runs one execution of the plugin; prints its output on failure
mvn_out=""
mvn_run() { mvn_out=$(../../mvnw -q -f clients "$plugin:$1" "${maven_args[@]}" 2>&1); }
# artifact <base> <artifact> [curl args...]: the artifact's type, if it exists
artifact() { local base=$1 id=$2; shift 2; curl -s "$base$V3/groups/maven-tls/artifacts/$id" "$@" | jq -r '.artifactType // .status'; }

for e in tls-pkcs12 tls-jks tls-pem dev-trust-all; do
  case $e in
    tls-pkcs12) what="PKCS12 trust store + username/password" ;;
    tls-jks) what="JKS trust store + username/password" ;;
    tls-pem) what="PEM CA certificate + username/password" ;;
    dev-trust-all) what="trustAll + verifyHostname=false, by IP (development only)" ;;
  esac
  if mvn_run "register@$e"; then
    check "register ($what)" AVRO "$(artifact "$REGISTRY" "$e" "${CA[@]}" "${DEV[@]}")"
  else
    fail "register ($what): $(tail -3 <<< "$mvn_out")"
  fi
done
for e in mtls-pkcs12 mtls-jks mtls-pem; do
  if mvn_run "register@$e"; then
    check "register over mutual TLS ($e)" AVRO "$(artifact "$MTLS" "$e" "${CA[@]}" "${CLIENT[@]}")"
  else
    fail "register over mutual TLS ($e): $(tail -3 <<< "$mvn_out")"
  fi
done
# refused <execution> <description> <pattern>: the execution must fail, with output matching pattern
refused() {
  if mvn_run "register@$1"; then
    fail "$2: the plugin succeeded"
  elif grep -qE "$3" <<< "$mvn_out"; then
    ok "$2"
  else
    fail "$2: failed, but not with /$3/: $(grep -m1 -E 'ERROR|Exception' <<< "$mvn_out")"
  fi
}
refused no-trust-store "no trust store: refused (PKIX path building failed)" 'PKIX|unable to find valid certification path'
refused hostname-mismatch "the right CA, by IP: refused (no name matches 127.0.0.1)" 'No subject alternative|doesn.t match|hostname'
refused wrong-password "TLS fine, wrong password: 401" '401|Unauthorized|not authenticated'
# The proxy's 400 is an HTML page, which the plugin reports as "Content type text/html does not have
# a factory to be parsed" rather than as a 400
refused mtls-no-client-cert "mutual TLS, no client certificate: refused (the proxy's 400 page)" 'text/html'
refused mtls-rogue-client-cert "mutual TLS, certificate from another CA: refused (the proxy's 400 page)" 'text/html'
rm -rf clients/target/downloaded
schema=$(jq -c . clients/src/main/resources/greeting.avsc)
if mvn_run download@download-mtls; then
  check "download over mutual TLS: the schema registered above" "$schema" "$(jq -c . clients/target/downloaded/mtls-pkcs12.avsc)"
else
  fail "download over mutual TLS: $(tail -3 <<< "$mvn_out")"
fi
if mvn_run download@download-tls; then
  check "download over TLS with credentials" "$schema" "$(jq -c . clients/target/downloaded/tls-pkcs12.avsc)"
else
  fail "download over TLS with credentials: $(tail -3 <<< "$mvn_out")"
fi

# ------------------------------------------------------------------------------ steps 7 to 9
part "Steps 7 to 9: the Java SDK and the Kafka serdes"
if out=$(../../mvnw -q -f clients exec:java "${maven_args[@]}" -Dkafka.bootstrap="$KAFKA" 2>&1); then
  grep -E '^(  ok |Java SDK|Kafka serdes)' <<< "$out" || true
  ok "the clients' checks passed"
else
  grep -E '^(  (ok|FAIL) |Java SDK|Kafka serdes)' <<< "$out" || tail -30 <<< "$out"
  fail "the clients failed"
fi

if [[ $failures -gt 0 ]]; then
  echo
  echo "$failures check(s) failed."
  exit 1
fi
echo
echo "All checks passed."
