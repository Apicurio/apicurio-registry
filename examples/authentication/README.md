# Authentication

A complete, local example of every way Apicurio Registry authenticates callers, with one Keycloak
and four registries:

- **OIDC bearer tokens** for users and service accounts, with roles; HTTP basic credentials exchanged
  for tokens, with the token cache; the web console's login.
- **HTTP basic** against users configured in the registry.
- **An authenticating reverse proxy** (nginx) passing the identity in headers.
- **Kubernetes service account tokens**, checked with the TokenReview API of a k3s server.
- **Clients:** the Java SDK, Kafka serializers, the Maven plugin and the CLI.

Every check covers both sides: who gets in, and who gets 401 or 403. For which resources an
authenticated caller may use, see [per-resource authorization](../per-resource-authorization/).

**New to this? Follow the [step-by-step guide](GUIDE.md)** (about 40 minutes).

## Quick start

Requires Docker (or Podman) with Compose v2 (k3s runs privileged), `curl`, `jq` and JDK 21 or later.

```bash
docker compose up -d     # Keycloak, four registries, the console, nginx, k3s and Kafka
./demo.sh                # checks every mechanism and client
                         # (its first run builds the clients, the Maven plugin and the CLI)
```

`demo.sh` ends with `All checks passed.`. Then open the console at <http://localhost:8888> and log in
as `developer` / `developer`.

Stop and remove everything with `docker compose down -v`.

## Services

| Service | URL | Authenticates with |
|---|---|---|
| Keycloak | http://localhost:8080 | Realm `registry` (admin console: `admin` / `admin`) |
| `registry` | http://localhost:8081 | OIDC bearer tokens; basic as client credentials |
| `registry-ui` | http://localhost:8888 | OIDC login (users `admin`, `developer`, `user`; password = name) |
| `registry-basic` | http://localhost:8082 | Basic: `admin`, `developer`, `readonly` (password = name) |
| `proxy` | http://localhost:8083 | Basic at nginx: `alice` (developer), `bob` (read-only); password = name |
| `registry-k8s` | http://localhost:8084 | Kubernetes tokens: `kubectl -n payments create token app` |
| Kafka | localhost:9092 | (for the serializers) |

Service accounts in Keycloak: `admin-client`, `developer-client`, `readonly-client`, `no-role-client`,
secret `test1`.

## Files

| File | Purpose |
|------|---------|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough and presenter notes |
| [`images/`](images) | The guide's screenshots; [`capture-screenshots.cjs`](capture-screenshots.cjs) refreshes them |
| [`docker-compose.yml`](docker-compose.yml) | The services; each registry's authentication is in its `environment` |
| [`keycloak/realm.json`](keycloak/realm.json) | Users, service accounts, roles, and login events (for the token cache check) |
| [`proxy/`](proxy) | nginx configuration and `htpasswd` |
| [`k8s/setup.sh`](k8s/setup.sh) | Creates the service accounts in k3s and the registry's kubeconfig |
| [`clients/`](clients) | Java SDK and serdes client, and the Maven plugin configuration |
| [`build.sh`](build.sh) | Builds the clients, the Maven plugin and the CLI from this checkout |
| [`demo.sh`](demo.sh) | Checks every mechanism and client |
| [`ci.env`](ci.env) | CI settings |

## Notes

- **Known issues** (the guide shows each): a malformed basic header returns 500
  ([#11043](https://github.com/Apicurio/apicurio-registry/issues/11043)); resetting a dynamic setting
  with `DELETE` has no effect until restart ([#11044](https://github.com/Apicurio/apicurio-registry/issues/11044));
  the SDK's `bearerToken()` needs the JDK adapter ([#11046](https://github.com/Apicurio/apicurio-registry/issues/11046)).
- **Local only:** Keycloak in development mode over HTTP, the token issuer check relaxed, secrets in
  plain text, k3s privileged and TLS verification skipped. Do not use this configuration in
  production.

## Learn more

- *Configuring Registry security* in the Registry documentation
  ([source](../../docs/modules/ROOT/pages/getting-started/assembly-configuring-registry-security.adoc)).
- [ADR-0009](../../adr/0009-authentication.md): how authentication is implemented.
- [Per-resource authorization](../per-resource-authorization/) and [ADR-0005](../../adr/0005-per-resource-authorization.md).
