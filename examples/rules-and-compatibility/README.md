# Rules and compatibility

A complete, local example of Apicurio Registry's content rules:

- **Validity** rejects content that is not a valid schema.
- **Compatibility** rejects a new version that would break the readers or writers of earlier versions:
  BACKWARD, FORWARD, FULL, their transitive variants, and NONE.
- **Integrity** checks a schema's references to other artifacts.
- Rules are set globally, per group and per artifact; the most specific one wins, per rule type.
- The **client-side validation library** checks data, not schemas, against a schema in the registry.

**New to this? Follow the [step-by-step guide](GUIDE.md).** It walks through every rule in the API
and in the UI, with commands, expected output and screenshots, in about 30 minutes.

## Quick start

Requires Docker (or Podman) with Compose v2, `curl` and `jq`.

```bash
docker compose up -d     # starts the Registry and its UI
./demo.sh                # creates sample data, then checks every rule behaviour
```

`demo.sh` prints one line per check and ends with `All checks passed.` (it exits non-zero if a check
fails). Then open the UI at <http://localhost:8888>.

To explore the API yourself, load the helper commands:

```bash
source ./demo-env.sh
call GET /groups/orders/artifacts/order-placed/rules
```

Stop and remove everything with `docker compose down -v`.

## Services

| Service               | URL                   | Purpose                        |
|-----------------------|-----------------------|--------------------------------|
| Apicurio Registry API | http://localhost:8081 | Registry, in-memory storage    |
| Apicurio Registry UI  | http://localhost:8888 | Web console                    |

## Files

| File | Purpose |
|------|---------|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough (API and UI) and presenter notes |
| [`images/`](images) | The guide's UI screenshots; [`capture-screenshots.cjs`](capture-screenshots.cjs) refreshes them |
| [`docker-compose.yml`](docker-compose.yml) | The services; the Registry configuration is in its `environment` |
| [`schemas/`](schemas) | The Avro and JSON schemas the steps register |
| [`demo.sh`](demo.sh) | Creates the sample data and checks every rule behaviour |
| [`demo-env.sh`](demo-env.sh) | Shell helpers (`status`, `call`) to call the API by hand |
| [`validation-client/`](validation-client) | Java client validating JSON and Avro data with the client-side validation library (guide step 13) |

## Notes

- **Other ports:** if 8081 or 8888 are in use, see [Using other ports](GUIDE.md#using-other-ports).
- **Local build:** the example uses the `latest-snapshot` images. To run a locally built registry,
  build it (`./mvnw install -pl distro/docker -am -DskipTests`), build
  `distro/docker/target/docker/Dockerfile.jvm` as an image, and start the example with
  `REGISTRY_IMAGE=<that image> docker compose up -d`.
- **Validation client:** it needs the validation library built from this checkout (guide step 13).
  CI runs it in the *Compile Java Examples* job; `demo.sh` covers the server-side rules.
- **Known issues:** publishing a draft under a non-transitive rule compares it with itself
  ([#11001](https://github.com/Apicurio/apicurio-registry/issues/11001)); an invalid rule config is
  accepted and then breaks writes ([#11000](https://github.com/Apicurio/apicurio-registry/issues/11000)).
- **Local only:** in-memory storage, no authentication. Do not use this configuration in production.

## Learn more

- *Introduction to registry rules*, *Registry rule reference* and *Registry compatibility modes* in
  the Registry documentation ([source](../../docs/modules/ROOT/pages/getting-started/)).
- [ADR-0006](../../adr/0006-rules-engine.md): how rules are implemented.
