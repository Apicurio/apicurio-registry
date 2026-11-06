# <Feature>

<!-- Template: see ../TEMPLATE.md. Replace every <placeholder> and delete these comments. -->

A complete, local example of Apicurio Registry's <feature>:

- **<Component>** <what it does in this example>.
- **<Component>** <what it does in this example>.

**New to this? Follow the [step-by-step guide](GUIDE.md).** It walks through every feature in the
API and in the UI, with commands, expected output and screenshots, in about <N> minutes.

## Quick start

Requires Docker (or Podman) with Compose v2, `curl` and `jq`.

```bash
docker compose up -d     # starts the Registry and its UI
./demo.sh                # creates sample data, then checks the expected behaviour
```

`demo.sh` prints one line per check and ends with `All checks passed.` (it exits non-zero if a check
fails). Then open the UI at <http://localhost:8888>.

To explore the API yourself, load the helper commands:

```bash
source ./demo-env.sh
call GET /groups/<group>/artifacts
```

Stop and remove everything with `docker compose down -v`.

## Services

| Service               | URL                   | Purpose     |
|-----------------------|-----------------------|-------------|
| Apicurio Registry API | http://localhost:8081 | <Purpose>   |
| Apicurio Registry UI  | http://localhost:8888 | Web console |

## Files

| File | Purpose |
|------|---------|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough (API and UI) and presenter notes |
| [`images/`](images) | The guide's UI screenshots |
| [`docker-compose.yml`](docker-compose.yml) | The services; the Registry configuration is in its `environment` |
| [`demo.sh`](demo.sh) | Creates the sample data and checks the expected behaviour |
| [`demo-env.sh`](demo-env.sh) | Shell helpers (`status`, `call`) to call the API by hand |

## Notes

- **Other ports:** if 8081 or 8888 are in use, see [Using other ports](GUIDE.md#using-other-ports).
- **Local build:** the example uses the `latest-snapshot` images. To run a locally built registry,
  build it (`./mvnw install -pl distro/docker -am -DskipTests`), then build
  `distro/docker/target/docker/Dockerfile.jvm` as `apicurio/apicurio-registry:latest-snapshot`.
- **Local only:** <what makes this configuration unsuitable for production>.

## Learn more

- *<Docs title>* in the Registry documentation
  ([source](../../docs/modules/ROOT/pages/getting-started/assembly-<topic>.adoc)).
- [ADR-NNNN](../../adr/NNNN-<title>.md): how it is implemented.
