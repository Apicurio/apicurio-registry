# Custom artifact types

Adds two artifact types of its own to Apicurio Registry, and checks that they behave like built-in
ones (detection, `VALIDITY` and `COMPATIBILITY` rules, canonical lookups):

- **`MARKDOWN`**, implemented by **Java classes** loaded into the registry image: the image derives
  from the re-augmentable `-mutable` image, without rebuilding the registry.
- **`CSV`** (the header line of a CSV file), implemented by a **webhook** service, with the
  standard provider contract; it works with any image.

**New to this? Follow the [step-by-step guide](GUIDE.md).**

## Quick start

Requires Docker (or Podman) with Compose v2, `curl` and `jq`, and JDK 21 for the first build.

```bash
./demo.sh          # build the jar and the image, start, check, remove everything
KEEP=1 ./demo.sh   # leave the stack running; the UI is at http://localhost:8888
```

It ends with `All checks passed.`. `REGISTRY_MUTABLE_IMAGE` selects the base image (default
`apicurio/apicurio-registry:latest-snapshot-mutable`); the jar must be built against the same
registry minor version.

## Services

| Service | Port | Role |
|---|---|---|
| `apicurio-registry` | 8080 | Built from the [`Dockerfile`](Dockerfile): the `-mutable` image, the MARKDOWN jar, re-augmented, and [`artifact-types.json`](artifact-types.json) |
| `csv-webhook` | | The CSV providers ([`webhook/server.py`](webhook/server.py), Python standard library) |
| `apicurio-registry-ui` | 8888 | The registry UI |

## Files

| File | Purpose |
|------|---------|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough and presenter notes |
| [`artifact-types.json`](artifact-types.json) | Declares MARKDOWN (Java classes) and CSV (webhooks), and keeps the standard types |
| [`src/main/java/.../Markdown*.java`](src/main/java/io/apicurio/registry/examples/customtypes/) | The MARKDOWN providers: accepter, validator, compatibility checker, canonicalizer |
| [`webhook/server.py`](webhook/server.py) | The CSV providers, one URL each |
| [`Dockerfile`](Dockerfile) | The derived image: `providers/` plus `build.sh --prune` |
| [`samples/`](samples/) | Markdown documents used by the demo |
| [`build.sh`](build.sh) | Builds the provider jar from this checkout |
| [`demo.sh`](demo.sh) | Builds, starts and checks everything |
| [`ci.env`](ci.env) | CI settings: this commit's `-mutable` image |

## Notes

- The configured webhook `headers` are sent with every request, and the ContentAccepter webhook
  returns the contract's `{"accepted": ...}` object; `demo.sh` checks both.
- With the webhook down, the rules that use it reject the change with a generic message, after about
  10 seconds of retries.
- **Local only:** no authentication, no TLS.

## Learn more

- *Configuring custom artifact types* in the Registry documentation
  ([source](../../docs/modules/ROOT/pages/getting-started/assembly-custom-artifact-types.adoc)).
- [ADR-0013](../../adr/0013-custom-artifact-types.md): the provider model, and why there are no
  script providers.
