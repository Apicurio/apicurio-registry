#!/usr/bin/env bash
# Checks how each registry in this example authenticates callers, and what each caller may do.
# Usage: ./demo.sh   (after `docker compose up -d`; requires curl, jq and JDK 21 or later)
# Prints one ok/FAIL line per check, ends with "All checks passed." and exits non-zero on failure.
# Safe to run again: it starts by deleting the groups it creates (and only those).
set -euo pipefail
cd "$(dirname "$0")"

KEYCLOAK=${KEYCLOAK:-http://localhost:8080}
REGISTRY=${REGISTRY:-http://localhost:8081}
BASIC=${BASIC:-http://localhost:8082}
PROXY=${PROXY:-http://localhost:8083}
K8S=${K8S:-http://localhost:8084}
KAFKA=${KAFKA:-localhost:9092}
TOKEN_URL="$KEYCLOAK/realms/registry/protocol/openid-connect/token"
V3=/apis/registry/v3
failures=0

ok()   { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
part() { printf '\n%s\n' "$1"; }
check() { if [[ "$3" == "$2" ]]; then ok "$1"; else fail "$1: got '$3', expected '$2'"; fi; }

# code <base> <method> <path> [curl args...]: the HTTP status of a request
code() {
  local base=$1 method=$2 resource=$3
  shift 3
  curl -s --max-time 30 -o /dev/null -w '%{http_code}' -X "$method" "$base$V3$resource" "$@"
}
# write_group <base> <group> [curl args...]: the status of creating a group (a write)
write_group() {
  local base=$1 group=$2
  shift 2
  code "$base" POST /groups -H 'Content-Type: application/json' -d "{\"groupId\": \"$group\"}" "$@"
}
# user_token <user>: a user's token (password grant, public client of the web console)
user_token() {
  curl -s --max-time 30 "$TOKEN_URL" -d "grant_type=password&client_id=apicurio-registry&username=$1&password=$1" \
    | jq -r .access_token
}
# client_token <client> <secret>: a service account's token (client credentials grant)
client_token() {
  curl -s --max-time 30 "$TOKEN_URL" -d "grant_type=client_credentials&client_id=$1&client_secret=$2" | jq -r .access_token
}
bearer() { echo "Authorization: Bearer $1"; }

wait_for() {
  local deadline=$((SECONDS + ${WAIT_TIMEOUT:-300}))
  until curl -s --max-time 5 -o /dev/null "$2"; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "Timed out waiting for $1 at $2 (is 'docker compose up' running?)" >&2
      exit 1
    fi
    sleep 2
  done
}

echo "Waiting for Keycloak and the registries..."
wait_for Keycloak "$KEYCLOAK/realms/registry"
for r in "$REGISTRY" "$BASIC" "$PROXY" "$K8S"; do wait_for Registry "$r$V3/system/info"; done

if [ ! -d clients/target/classes ] || [ ! -f ../../cli/target/quarkus-app/quarkus-run.jar ]; then
  echo "Building the clients, the Maven plugin and the CLI (first run only, a few minutes)..."
  log=$(mktemp)
  ./build.sh > "$log" 2>&1 || { tail -40 "$log"; echo "build.sh failed; full log: $log" >&2; exit 1; }
fi

ADMIN=$(client_token admin-client test1)
echo "Resetting the example data..."
code "$REGISTRY" PUT /admin/config/properties/apicurio.auth.anonymous-read-access.enabled \
  -H "$(bearer "$ADMIN")" -H 'Content-Type: application/json' -d '{"value": "false"}' > /dev/null
for g in user-admin user-developer user-user sa-admin sa-developer basic-cc sdk-oauth2 maven cli-oauth2; do
  code "$REGISTRY" DELETE "/groups/$g" -H "$(bearer "$ADMIN")" > /dev/null
done
code "$REGISTRY" DELETE /groups/default/artifacts/greetings-value -H "$(bearer "$ADMIN")" > /dev/null
for g in basic-admin basic-developer sdk-basic cli-basic; do code "$BASIC" DELETE "/groups/$g" -u admin:admin > /dev/null; done
for g in proxy-alice; do code "$PROXY" DELETE "/groups/$g" -u alice:alice > /dev/null; done
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --delete \
  --if-exists --topic greetings > /dev/null 2>&1 || true

# ------------------------------------------------------------------------------ step 4
part "Step 4: anonymous callers (registry, OIDC)"
check "anonymous read: 401" 401 "$(code "$REGISTRY" GET /groups)"
check "the 401 says why" "User is not authenticated." "$(curl -s "$REGISTRY$V3/groups" | jq -r .title)"
check "anonymous read access switched on at runtime (admin)" 204 \
  "$(code "$REGISTRY" PUT /admin/config/properties/apicurio.auth.anonymous-read-access.enabled \
     -H "$(bearer "$ADMIN")" -H 'Content-Type: application/json' -d '{"value": "true"}')"
check "now anonymous read: 200" 200 "$(code "$REGISTRY" GET /groups)"
check "anonymous write: still 401" 401 "$(write_group "$REGISTRY" anonymous)"
code "$REGISTRY" PUT /admin/config/properties/apicurio.auth.anonymous-read-access.enabled \
  -H "$(bearer "$ADMIN")" -H 'Content-Type: application/json' -d '{"value": "false"}' > /dev/null
check "switched off again (set to false, see #11044): 401" 401 "$(code "$REGISTRY" GET /groups)"

# ------------------------------------------------------------------------------ step 5
part "Step 5: users with OIDC tokens, and their roles"
for u in admin developer user; do
  t=$(user_token "$u")
  case $u in admin) expected="200 200 200" ;; developer) expected="200 200 403" ;; user) expected="200 403 403" ;; esac
  check "$u: read, write, admin API" "$expected" \
    "$(code "$REGISTRY" GET /groups -H "$(bearer "$t")") $(write_group "$REGISTRY" "user-$u" -H "$(bearer "$t")") $(code "$REGISTRY" GET /admin/config/properties -H "$(bearer "$t")")"
done
check "an invalid token: 401" 401 "$(code "$REGISTRY" GET /groups -H 'Authorization: Bearer not.a.token')"

# ------------------------------------------------------------------------------ step 6
part "Step 6: service accounts (client credentials)"
check "admin-client: write" 200 "$(write_group "$REGISTRY" sa-admin -H "$(bearer "$ADMIN")")"
check "developer-client: write" 200 "$(write_group "$REGISTRY" sa-developer -H "$(bearer "$(client_token developer-client test1)")")"
check "readonly-client: write is forbidden" 403 "$(write_group "$REGISTRY" sa-readonly -H "$(bearer "$(client_token readonly-client test1)")")"
check "no-role-client: authenticated, but no role, so even reads are forbidden" 403 \
  "$(code "$REGISTRY" GET /groups -H "$(bearer "$(client_token no-role-client test1)")")"
check "the group's owner is the service account" developer-client \
  "$(curl -s -H "$(bearer "$ADMIN")" "$REGISTRY$V3/groups/sa-developer" | jq -r .owner)"

# ------------------------------------------------------------------------------ step 7
part "Step 7: HTTP basic exchanged for a token (client credentials)"
check "developer-client:test1 as basic credentials: write" 200 "$(write_group "$REGISTRY" basic-cc -u developer-client:test1)"
check "a wrong secret: 401" 401 "$(code "$REGISTRY" GET /groups -u developer-client:wrong)"
check "user:password is not a client: 401 (no password grant configured)" 401 "$(code "$REGISTRY" GET /groups -u developer:developer)"
check "a malformed basic header: 500 today, should be 401 (#11043)" 500 \
  "$(code "$REGISTRY" GET /groups -H 'Authorization: Basic !!!notbase64')"
master=$(curl -s "$KEYCLOAK/realms/master/protocol/openid-connect/token" \
  -d 'grant_type=password&client_id=admin-cli&username=admin&password=admin' | jq -r .access_token)
logins() {
  curl -s -H "$(bearer "$master")" "$KEYCLOAK/admin/realms/registry/events?type=CLIENT_LOGIN&client=readonly-client&max=1000" | jq length
}
code "$REGISTRY" GET /groups -u readonly-client:test1 > /dev/null
before=$(logins)
for _ in 1 2 3 4 5; do code "$REGISTRY" GET /groups -u readonly-client:test1 > /dev/null; done
check "5 more calls with the same credentials: no new token requests (cached)" 0 "$(($(logins) - before))"

# ------------------------------------------------------------------------------ step 8
part "Step 8: HTTP basic against users configured in the registry (registry-basic)"
check "admin: write" 200 "$(write_group "$BASIC" basic-admin -u admin:admin)"
check "developer: write" 200 "$(write_group "$BASIC" basic-developer -u developer:developer)"
check "readonly: read, write" "200 403" "$(code "$BASIC" GET /groups -u readonly:readonly) $(write_group "$BASIC" basic-readonly -u readonly:readonly)"
check "a wrong password, and no credentials: 401" "401 401" "$(code "$BASIC" GET /groups -u admin:wrong) $(code "$BASIC" GET /groups)"
check "a malformed basic header: 401" 401 "$(code "$BASIC" GET /groups -H 'Authorization: Basic !!!notbase64')"

# ------------------------------------------------------------------------------ step 9
part "Step 9: identity from a trusted proxy (proxy -> registry-proxy)"
check "alice (proxy group sr-developer): write" 200 "$(write_group "$PROXY" proxy-alice -u alice:alice)"
check "bob (proxy group sr-readonly): read, write" "200 403" "$(code "$PROXY" GET /groups -u bob:bob) $(write_group "$PROXY" proxy-bob -u bob:bob)"
check "the proxy rejects a wrong password, and no credentials" "401 401" "$(code "$PROXY" GET /groups -u alice:wrong) $(code "$PROXY" GET /groups)"
check "bob cannot promote himself by sending the header: the proxy overwrites it" 403 \
  "$(write_group "$PROXY" proxy-spoof -u bob:bob -H 'X-Forwarded-Groups: sr-admin')"
check "the owner is the user the proxy authenticated" alice \
  "$(curl -s -u alice:alice "$PROXY$V3/groups/proxy-alice" | jq -r .owner)"

# ------------------------------------------------------------------------------ step 10
part "Step 10: Kubernetes service account tokens (registry-k8s, TokenReview with k3s)"
k8s_token() { docker compose exec -T k3s kubectl -n "$1" create token app --duration 1h; }
payments=$(k8s_token payments)
auditors=$(k8s_token auditors)
check "payments/app (group system:serviceaccounts:payments -> developer): write" 200 \
  "$(write_group "$K8S" k8s-payments -H "$(bearer "$payments")")"
check "auditors/app (-> readonly): read, write" "200 403" \
  "$(code "$K8S" GET /groups -H "$(bearer "$auditors")") $(write_group "$K8S" k8s-auditors -H "$(bearer "$auditors")")"
check "the owner is the service account's user name" system:serviceaccount:payments:app \
  "$(curl -s -H "$(bearer "$payments")" "$K8S$V3/groups/k8s-payments" | jq -r .owner)"
check "a Keycloak token means nothing here: 401" 401 "$(code "$K8S" GET /groups -H "$(bearer "$ADMIN")")"
code "$K8S" DELETE /groups/k8s-payments -H "$(bearer "$payments")" > /dev/null

# ------------------------------------------------------------------------------ step 11
part "Step 11: Java clients (SDK and Kafka serdes)"
if out=$(../../mvnw -q -f clients exec:java -Dregistry.url="$REGISTRY$V3" -Dbasic.url="$BASIC$V3" \
    -Dtoken.endpoint="$TOKEN_URL" -Dkafka.bootstrap="$KAFKA" 2>&1); then
  grep -E '^  ok ' <<< "$out"
  ok "the clients' checks passed"
else
  grep -E '^  (ok|FAIL) ' <<< "$out" || tail -30 <<< "$out"
  fail "the clients failed"
fi

# ------------------------------------------------------------------------------ step 12
part "Step 12: the Maven plugin and the CLI"
version=$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' clients/pom.xml | head -1)
if ../../mvnw -q -f clients "io.apicurio:apicurio-registry-maven-plugin:$version:register" \
    -Dregistry.url="$REGISTRY$V3" -Dtoken.endpoint="$TOKEN_URL" > /dev/null 2>&1; then
  ok "the Maven plugin registered maven/greeting with client credentials"
else
  fail "the Maven plugin could not register maven/greeting"
fi
check "its owner is developer-client" developer-client \
  "$(curl -s -H "$(bearer "$ADMIN")" "$REGISTRY$V3/groups/maven/artifacts/greeting" | jq -r .owner)"
acr_home=$(mktemp -d)
mkdir -p "$acr_home/current"
echo '{}' > "$acr_home/current/config.json"
acr() { ACR_HOME="$acr_home" ACR_CURRENT_HOME="$acr_home/current" java -jar ../../cli/target/quarkus-app/quarkus-run.jar "$@" 2>&1; }
acr context create oidc "$REGISTRY" > /dev/null
acr login --client-id developer-client --client-secret test1 --token-endpoint "$TOKEN_URL" \
  --allow-unsafe-credential-storage > /dev/null
acr group create cli-oauth2 > /dev/null || true
check "CLI logged in with client credentials and created a group" 200 \
  "$(code "$REGISTRY" GET /groups/cli-oauth2 -H "$(bearer "$ADMIN")")"
acr context create basic "$BASIC" > /dev/null
acr login --username developer --password developer --allow-unsafe-credential-storage > /dev/null
acr group create cli-basic > /dev/null || true
check "CLI logged in with a user and password (registry-basic) and created a group" 200 \
  "$(code "$BASIC" GET /groups/cli-basic -u admin:admin)"
rm -rf "$acr_home"

if [[ $failures -gt 0 ]]; then
  echo
  echo "$failures check(s) failed."
  exit 1
fi
echo
echo "All checks passed."
