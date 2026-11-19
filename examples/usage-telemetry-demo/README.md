# Usage telemetry

Which applications read which schema versions, and what that tells you: who is falling behind,
whether a version is safe to deprecate, and which schemas nobody uses any more.

- Client applications identify themselves with `X-Registry-Client-Id` (the Apicurio serdes send it
  when `apicurio.registry.usage-telemetry.client-id` is set); the registry records each schema fetch.
- `demo.sh` plays five applications and checks the usage metrics, the consumer heatmap and drift
  alerts, deprecation readiness, the Active/Stale/Dead summary, and liveness inherited through
  references.

**New to this? Follow the [step-by-step guide](GUIDE.md).**

## Quick start

Requires Docker (or Podman) with Compose v2, `curl` and `jq`.

```bash
docker compose up -d
./demo.sh
```

It ends with `All checks passed.`; the console is on http://localhost:8888 (open an artifact's
**Usage** tab). Each run uses a new group, so it can be run again. `docker compose down -v` removes
everything.

## Services

| Service | Port | Role |
|---|---|---|
| `registry` | 8080 | Usage telemetry on (experimental), events flushed every 2 seconds |
| `ui` | 8888 | The console |

Override the ports with `REGISTRY_PORT` and `UI_PORT`.

## Files

| File | Purpose |
|------|---------|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough and presenter notes |
| [`docker-compose.yml`](docker-compose.yml) | The registry and the console |
| [`demo.sh`](demo.sh) | Plays the applications and checks what is recorded |
| [`images/`](images) | The guide's screenshot |

## Notes

- **Known issue:** the console labels a consumer that is behind, but not yet at the drift-alert
  threshold, as "Current" ([#11212](https://github.com/Apicurio/apicurio-registry/issues/11212)).
- Telemetry is experimental. On KafkaSQL every usage event is also a journal record: see the GA
  checklist in ADR-0003 before enabling it there.
- **Local only:** no authentication; in a real deployment the usage endpoints need the admin role.

## Learn more

- *Usage telemetry* in the Registry documentation
  ([source](../../docs/modules/ROOT/pages/getting-started/assembly-usage-telemetry.adoc)).
- [ADR-0003](../../adr/0003-usage-telemetry.md): the design, the storage parity and the GA checklist.
- [`kafka-serdes`](../kafka-serdes/): the serdes that send the headers.
