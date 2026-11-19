# Custom artifact types: step-by-step guide

Apicurio Registry knows Avro, Protobuf, OpenAPI and a dozen other types. This guide adds two of its
own, one written in Java and one served by a webhook, and checks that rules and lookups treat them
like the built-in ones.

**Time:** about 25 minutes. **You will see:**

- how the set of artifact types is configured, next to the built-in ones;
- a type implemented by Java classes, added to the registry image without rebuilding the registry;
- a type implemented by an HTTP service, with the standard image;
- what each provider does: detection, validity, compatibility, canonical form;
- what happens when a webhook is down.

For the reference documentation, see *Configuring custom artifact types* in the Registry
documentation ([source](../../docs/modules/ROOT/pages/getting-started/assembly-custom-artifact-types.adoc)).
For the design, see [ADR-0013](../../adr/0013-custom-artifact-types.md).

## Before you start

You need Docker with Compose v2 (or Podman), `curl`, `jq`, JDK 21 for the first build, and free
ports 8080 and 8888.

```bash
cd examples/custom-artifact-types
./demo.sh
```

`demo.sh` builds the provider jar (`build.sh`, once) and the registry image, starts everything, runs
the steps below, and removes everything. It ends with `All checks passed.`. Use `KEEP=1 ./demo.sh`
to look around afterwards.

## Step 1: The configuration file

[`artifact-types.json`](artifact-types.json) declares the types. The registry reads it from
`apicurio.artifact-types.config-file`:

```json
{
  "includeStandardArtifactTypes": true,
  "artifactTypes": [
    {"artifactType": "MARKDOWN", "contentTypes": ["text/markdown", "text/plain"],
     "contentAccepter":      {"type": "java", "classname": "...MarkdownContentAccepter"},
     "contentValidator":     {"type": "java", "classname": "...MarkdownContentValidator"},
     "compatibilityChecker": {"type": "java", "classname": "...MarkdownCompatibilityChecker"},
     "contentCanonicalizer": {"type": "java", "classname": "...MarkdownContentCanonicalizer"}},
    {"artifactType": "CSV", "contentTypes": ["text/csv"],
     "contentAccepter":      {"type": "webhook", "url": "http://csv-webhook:8080/accept"},
     ...}
  ]
}
```

Each operation has its own provider; leave one out and the registry skips that operation (no
detection, always valid, always compatible). `includeStandardArtifactTypes` keeps the built-in
types: `GET /apis/registry/v3/admin/config/artifactTypes` lists MARKDOWN and CSV next to AVRO and
OPENAPI.

## Step 2: MARKDOWN, in Java

The four classes in [`src/main/java`](src/main/java/io/apicurio/registry/examples/customtypes/)
implement the interfaces of `apicurio-registry-schema-util-common`. A document must start with a
level-1 heading (its title); level-2 headings are its sections.

**Getting the classes into the registry.** The registry is a Quarkus application whose class path is
fixed when it is built. The `-mutable` image can be rebuilt with extra jars, in a derived image:

```dockerfile
FROM apicurio/apicurio-registry:latest-snapshot-mutable
COPY target/custom-artifact-types.jar /deployments/quarkus-app/providers/
COPY artifact-types.json /deployments/artifact-types.json
RUN /deployments/build.sh --prune
ENV APICURIO_ARTIFACT_TYPES_CONFIG_FILE=/deployments/artifact-types.json
```

`build.sh` re-augments the application (about 10 seconds, about 1 GB of memory at build time);
`--prune` removes the build-time jars, so the image is no bigger than the standard one. Runtime and
startup are unchanged. Rebuild the jar against each new registry minor version.

`demo.sh` then checks each provider:

| Provider | Check |
|---|---|
| `contentAccepter` | An artifact created **without** `artifactType` is detected as MARKDOWN |
| `contentValidator` | With `VALIDITY=FULL`, a document without a title is rejected (400) |
| `compatibilityChecker` | With `COMPATIBILITY=BACKWARD`, removing a section is rejected; adding one is version 2 |
| `contentCanonicalizer` | The first document with trailing spaces, sent with `canonical=true`, is found as version 1 |

## Step 3: CSV, as a webhook

[`webhook/server.py`](webhook/server.py) is a small HTTP service (Python standard library). A CSV
schema is a header line, `id,name,email`. The registry POSTs a JSON request to each provider's URL:

| URL | Request | Response |
|---|---|---|
| `/accept` | `{"typedContent": {...}}` | `true` or `false` |
| `/validate` | `{"function": "validate", "level": "FULL", "content": {...}}` | `{"ruleViolations": [{"description": ..., "context": ...}]}` |
| `/compatibility` | `{"level": "BACKWARD", "existingArtifacts": [...], "proposedArtifact": {...}}` | `{"incompatibleDifferences": [...]}` |
| `/canonicalize` | `{"content": {...}}` | `{"typedContent": {...}}` |

The full contract is `common/src/main/resources/META-INF/artifact-type-webhooks.json`. `demo.sh`
checks the same four behaviours as for MARKDOWN: `text/csv` is detected as CSV, a duplicate column
is invalid, dropping a column is incompatible, and `ID,Name,Email` is found as `id,name,email`
canonically. The service records the requests it receives, and every URL was called.

Two differences from the contract today
([#11151](https://github.com/Apicurio/apicurio-registry/issues/11151)):

- `/accept` must return a bare `true`/`false`; the contract says `{"accepted": true}`.
- The `headers` configured for a webhook (here an `Authorization` header) are not sent. Do not rely
  on them to protect the service; keep it on a private network.

## Step 4: When the webhook is down

`demo.sh` stops `csv-webhook` and tries again:

- **`VALIDITY` fails closed:** the version is rejected with `Error invoking Content Validator webhook
  for 'CSV'.`; the cause is only logged.
- **`COMPATIBILITY` fails closed:** the change is rejected, with a generic message as well.
- The other providers (canonicalizer, reference finder, dereferencer) fail the request with a 502.
- Each request fails after about 10 seconds of retries. The connect and read timeouts of a webhook
  call are set by `apicurio.artifact-types.webhooks.connect-timeout.seconds` and
  `apicurio.artifact-types.webhooks.read-timeout.seconds`.

A webhook is a runtime dependency of every rule that uses it: run it with the same availability as
the registry.

## Step 5: Clean up

`demo.sh` removes everything. After `KEEP=1`: `docker compose down -v`.

## Presenter notes

- **The story in one sentence per step:** types are configuration (1); Java providers go into a
  derived image (2); webhooks work with any image and any language (3); and they are a dependency
  (4).
- **Questions that usually come up:**
  - *Java or webhook?* Java for speed and no extra service; webhooks for other languages or no
    image build.
  - *Can I write a type in JavaScript?* Not any more: script providers were removed in 3.x
    (ADR-0013).
  - *Does the UI support custom types?* It lists and shows them, with no dedicated icon or editor.

## Troubleshooting

| Symptom | What to do |
|---|---|
| MARKDOWN or CSV is missing from the types | Check the log for "Failed to load artifact type configuration file": a malformed file is ignored |
| A request fails with "class not found" | Java providers are loaded on first use: the jar is not in `providers/`, or `build.sh` was not run after copying it |
| Content is not detected as CSV | Send `contentType: text/csv`; `/accept` must return a bare boolean (#11151) |
| Requests fail after about 10 seconds with "Error invoking ... webhook" | The webhook is unreachable: check the registry log for the cause |
