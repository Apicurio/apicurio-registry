# HTTP caching: step-by-step guide

Most registry traffic is reads, and most of it is the same content again: serializers fetching a
schema by ID, builds fetching `branch=latest`, consoles opening the same artifact. A reverse proxy
cache can answer most of it without the registry. This guide puts Varnish in front of the registry
and shows what the registry tells the cache, what the cache does with it, and where that goes wrong.

**Time:** about 20 minutes. **You will see:**

- the three cacheability levels, and the headers that carry them;
- hits, misses and conditional requests (ETags and `304 Not Modified`);
- what is never cached;
- what goes stale, because nothing invalidates the cache;
- two KafkaSQL replicas behind one cache.

The reference is the [HTTP caching README](../../app/src/main/java/io/apicurio/registry/rest/cache/README.md);
the design is [ADR-0016](../../adr/0016-http-caching.md).

## Before you start

You need Docker with Compose v2 (or Podman), `curl` and `jq`, and free ports 8080 to 8084, 8888,
9000 and 6080 (or set the `*_PORT` variables in the README).

```bash
cd examples/http-caching
./demo.sh
```

`demo.sh` starts everything, runs the steps below and the full per-endpoint suite, and removes
everything. It ends with `All checks passed.`. Use `KEEP=1 ./demo.sh` to look around afterwards.

## Step 1: What the registry says about each response

With `apicurio.http-caching.enabled=true`, each cacheable `GET` response carries:

```
Surrogate-Control: public, max-age=864000, immutable    how long a cache may keep it
ETag: "contentId=1;references=PRESERVE"                  what it is, for revalidation
Vary: Accept, Accept-Encoding, Authorization             what else the cache key must include
X-Cache-Cacheability: HIGH                              the registry's assessment (informational)
```

`Surrogate-Control` is for the cache (Varnish strips it); browsers see no caching directive.
`demo.sh` checks each level:

| Level | Responses | Kept |
|---|---|---|
| `HIGH` | Content by global ID, content ID, content hash; a concrete, non-draft version's content | 10 days, `immutable` |
| `MODERATE` | `branch=latest` and other branches; drafts; `references=DEREFERENCE`/`REWRITE` | 30 seconds, then revalidated |
| `LOW` | Inbound references, and anything without a reliable ETag (random ETag, never revalidated) | 10 seconds |

The `Vary` header includes `Authorization`, so with authentication each caller's responses are
cached separately.

## Step 2: Through Varnish

[`varnish/default.vcl`](varnish/default.vcl) takes the TTL from `Surrogate-Control`, keeps
`MODERATE` entries three times longer for revalidation, and marks hits with `X-Debug-Cache`:

```bash
curl -sI localhost:8081/apis/registry/v3/ids/globalIds/1 | grep X-Debug-Cache   # MISS, then HIT
```

`demo.sh` checks:

- the first read is a `MISS`, the second a `HIT`;
- a request with `If-None-Match` and the ETag gets `304 Not Modified` with no body: this is how
  Varnish revalidates a stale `MODERATE` entry without downloading it again;
- writes go straight to the registry;
- a `404` is not cached: the registry sends no caching headers on errors, so an artifact created
  right after a miss is served at once.

## Step 3: What goes stale

The registry does not tell the cache when something changes. A `HIGH` entry is kept for 10 days on
the assumption that it never changes. That holds for content by content ID or hash, which are derived
from the content, but not for coordinates:

- `demo.sh` reads `reborn` version 1 (record `First`) through Varnish, deletes the artifact, and
  creates it again with record `Second`;
- the registry serves `Second`; **the cache serves `First`**, for up to 10 days
  ([#11183](https://github.com/Apicurio/apicurio-registry/issues/11183));
- a `PURGE` of that URL fixes it.

Purge (or ban) after anything that changes data behind existing URLs: deleting versions, imports,
restores, GitOps commits that change a version. On GitOps and KubernetesOps the global ID is derived
from the coordinates too, so purge the `/ids/globalIds/...` URL of a replaced version as well.
`MODERATE` entries fix themselves within their TTL.

## Step 4: Two replicas behind one cache

The `cluster` profile runs two registries on one KafkaSQL journal, in the `prod` profile, behind a
Varnish that sends each request to the next replica ([`varnish/cluster.vcl`](varnish/cluster.vcl);
`X-Debug-Backend` shows which one filled an entry). `demo.sh` checks:

- a write to replica A reaches replica B through the journal;
- both give **the same ETag** (in `prod` the ETag is a hash of the same inputs on every replica), so
  any replica could revalidate an entry another one filled;
- but a conditional request gets `200`, not `304`, even on the replica that issued the ETag: in
  `prod` the registry compares `If-None-Match` with the unhashed value
  ([#11207](https://github.com/Apicurio/apicurio-registry/issues/11207)). Revalidation still gives the
  right content, just without saving the download;
- `branch=latest` is served from the cache after a new version, until the entry expires (5 seconds
  here, 30 by default), then the new version: staleness is bounded by the `MODERATE` TTL.

A replica that lags the journal can serve, and the cache keep, the previous `branch=latest` for one
more TTL. `HIGH` content is not affected there: on KafkaSQL a recreated version gets a new global
ID.

## Step 5: The full suite

[`test/run-tests.sh`](test/run-tests.sh) checks every cached endpoint with its parameters (references,
drafts, references to drafts, inbound and outbound references): 93 checks. Its rules are in
[`test/README.md`](test/README.md). It needs `APICURIO_HTTP_CACHING_OPAQUE_ETAGS_ENABLED=false`,
which the single-registry stack sets.

## Step 6: Clean up

`demo.sh` removes everything. After `KEEP=1`: `docker compose --profile cluster down -v`.

## Operating Varnish

**Watching it:**

```bash
docker exec varnish-cache varnishstat -1 -f MAIN.cache_hit -f MAIN.cache_miss -f MAIN.backend_conn
docker exec varnish-cache varnishlog -g request -q "VCL_call eq 'HIT' or VCL_call eq 'MISS'"
docker exec varnish-cache varnishadm -T :6080 -S /tmp/varnish-secret backend.list
```

Aim for a hit rate above 80% on read-heavy workloads.

**Before production** (the VCL marks these with `PRODUCTION:`):

- restrict `PURGE` and `BAN` to trusted addresses (`acl purge_acl`); here anyone can purge. The VCL's
  `BAN` branch is never reached, because non-`GET` requests are passed before it: move it next to
  `PURGE` if you need it;
- remove the `X-Debug-*` headers, and consider removing `ETag` and `X-Cache-Cacheability`;
- size the cache (`-s malloc,...`; watch `MAIN.n_lru_nuked`);
- terminate TLS in front of Varnish, which does not do TLS.

**Clearing everything:**

```bash
docker exec varnish-cache varnishadm -T :6080 -S /tmp/varnish-secret "ban req.url ~ ."
```

**A registry in dev mode:** run
`../../mvnw quarkus:dev -pl app -Dapicurio.http-caching.enabled=true -Dapicurio.rest.mutability.artifact-version-content.enabled=true`
and `docker compose -f docker-compose-dev.yaml up` (Varnish on 8081, host network, in front of the
registry on 8080).

## Presenter notes

- **The story in one sentence per step:** the registry labels each response (1); Varnish follows the
  label, and revalidates with ETags (2); but it cannot know about deletes (3); replicas agree on
  ETags, and short TTLs bound staleness (4).
- **Questions that usually come up:**
  - *Why not `Cache-Control`?* Browsers and intermediate proxies would cache too; `Surrogate-Control`
    is for the cache you run.
  - *Is invalidation coming?* It is planned (PURGE/BAN from the registry), not implemented.

## Troubleshooting

| Symptom | What to do |
|---|---|
| Every request is a `MISS` | Check the registry sends `Surrogate-Control` (`curl -I localhost:8080/apis/registry/v3/ids/globalIds/1`); HTTP caching is off by default |
| Old content after a delete | Purge the URL (#11183) |
| Never a `304` in production | Known issue (#11207); content is still correct |
| `test/run-tests.sh` fails at "Setting Up Test Data" | It needs opaque ETags off, and `REGISTRY_MANAGEMENT_URL` when ports are changed |
