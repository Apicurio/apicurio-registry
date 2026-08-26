#!/usr/bin/env bash
# Updates the "postgres" Toxiproxy proxy (deployed by toxiproxy.yaml) from the default ~15ms
# +/- 5ms latency profile to a much more extreme one, for worst-case testing:
#   - 100ms +/- 50ms latency (a rough approximation of a degraded/cross-region link, rather than
#     the "same region, different AZ" profile the default represents)
#   - a 2% chance of a request timing out outright (the "timeout" toxic, toxicity=0.02),
#     approximating packet loss / a flaky network path
#
# Run after postgresql/deploy.sh has already stood up Toxiproxy. Idempotent - safe to re-run.
set -euo pipefail

kubectl -n default exec deployment/toxiproxy -c toxiproxy-config -- sh -c '
  curl -sf -X PATCH http://localhost:8474/proxies/postgres/toxics/postgres_latency \
    -H "Content-Type: application/json" \
    -d "{\"attributes\":{\"latency\":100,\"jitter\":50}}"
  curl -sf -X POST http://localhost:8474/proxies/postgres/toxics -H "Content-Type: application/json" \
    -d "{\"name\":\"postgres_timeout\",\"type\":\"timeout\",\"toxicity\":0.02,\"attributes\":{\"timeout\":0}}" \
    || true
'
echo "Toxiproxy 'postgres' proxy updated to the extreme profile (100ms +/- 50ms latency, 2% timeout)."
