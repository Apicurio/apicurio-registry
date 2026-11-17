# ADR-0016: HTTP Caching

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-17.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#11183](https://github.com/Apicurio/apicurio-registry/issues/11183),
  [#11207](https://github.com/Apicurio/apicurio-registry/issues/11207).
- **Scope:** How the v3 REST API lets a reverse proxy cache (Varnish in the example) keep responses:
  cacheability levels, the headers, ETags and conditional requests, invalidation, and correctness with
  several replicas and with polling storage. Not covered: the serdes' client-side schema caches
  ([ADR-0007](0007-kafka-serdes.md)).

## Context

Reads dominate registry traffic, and most of them repeat: serializers resolving an ID, builds fetching
`branch=latest`, consoles. A cache in front of the registry can absorb them, but only if the registry
says what may be kept and for how long, because some URLs always mean the same bytes (content by ID)
and others move (a branch, a draft). The registry runs as several replicas, on storage that can lag
(KafkaSQL) or be replaced wholesale (GitOps), and has no channel to tell a cache that something
changed.

## Decision

1. **Opt-in, for a cache the operator runs.** With `apicurio.http-caching.enabled`, cacheable `GET`
   responses carry `Surrogate-Control` (for a surrogate such as Varnish, not browsers), an `ETag`,
   `Vary` (`Accept`, `Accept-Encoding`, `Authorization` by default) and, optionally,
   `X-Cache-Cacheability`. Only 2xx responses get them; errors are never cacheable.
2. **Per-request cacheability from a strategy.** Each cached endpoint evaluates a strategy before
   running (`EntityIdContentCacheStrategy`, `VersionContentCacheStrategy`), which decides the level
   and builds the ETag from what determines the response:

   | Level | When | `Surrogate-Control` |
   |---|---|---|
   | `HIGH` | Content by global ID, content ID or hash; a concrete, non-draft version; `references=PRESERVE`; outbound references | `max-age=864000, immutable` |
   | `MODERATE` | Branch expressions, drafts, dereferenced or rewritten content, when a deterministic ETag exists | `max-age=30, must-revalidate` |
   | `LOW` | Inbound references, and anything without a deterministic ETag (a random one, never revalidated) | `max-age=10` |
   | `NONE` | Everything else, including writes | none |

3. **ETags are deterministic inputs, optionally hashed.** The ETag lists what the content depends on
   (content ID, reference mode, and with higher-quality ETags the reference tree's content IDs). In
   the `prod` profile it is a SHA-256 of that (`opaque-etags-enabled`), which hides internals and is
   the same on every replica. `If-None-Match` is answered with 304 before the endpoint runs.
4. **No invalidation.** The registry never purges. Freshness comes from the TTLs: `HIGH` relies on the
   content being immutable, `MODERATE` is short and revalidated.
5. **The cache configuration is the operator's.** The example VCL maps `Surrogate-Control` to the
   TTL, keeps `MODERATE` entries 3× longer for revalidation, disables grace (consistency over
   availability) and does not cache what has no caching header.

### Why `Surrogate-Control`

A shared surrogate under the operator's control can keep content much longer than any client should;
`Cache-Control` would let browsers and intermediate proxies keep it too.

### Why no invalidation yet

Purging needs to know every cache and every URL that represents a change (IDs, coordinates, branches,
reference trees), and to work across replicas. TTLs need none of that, at the cost of bounded
staleness for `MODERATE` and correctness relying on immutability for `HIGH`.

## Model

### Correctness

| Situation | Result |
|---|---|
| Content by content ID or content hash (`HIGH`) | Correct for ever: both are derived from the content |
| Content by global ID (`HIGH`) | Correct on SQL and KafkaSQL, where a recreated version gets a new global ID. On GitOps and KubernetesOps the global ID is derived from the coordinates ([ADR-0010](0010-storage-architecture.md)), so a commit that replaces a version's content keeps the URL and the old content is served for up to 10 days: purge it ([#11183](https://github.com/Apicurio/apicurio-registry/issues/11183)) |
| `branch=latest`, drafts (`MODERATE`) | Stale for at most one TTL (30 seconds by default) |
| A version's content by coordinates (`HIGH`) | **Stale for up to 10 days** after the version is deleted and recreated, or its content replaced by an import or a GitOps commit ([#11183](https://github.com/Apicurio/apicurio-registry/issues/11183)) |
| Several replicas | Same ETags on every replica; a replica that lags the KafkaSQL journal can fill an entry with the previous `branch=latest` for one more TTL |
| Errors (404, 403) | Never cached |
| Authenticated callers | Separate entries per `Authorization` value (`Vary`) |

### Conditional requests

A cache revalidating a stale `MODERATE` entry sends `If-None-Match`; an unchanged response is a 304
with no body. In the `prod` profile this never matches today: the registry sends the hashed ETag but
compares with the unhashed one, so revalidation always downloads the content again
([#11207](https://github.com/Apicurio/apicurio-registry/issues/11207)). Results stay correct.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `apicurio.http-caching.enabled` | `false` | Send caching headers |
| `apicurio.http-caching.high-cacheability.max-age-seconds` | `864000` | `HIGH` TTL (0 disables) |
| `apicurio.http-caching.moderate-cacheability.max-age-seconds` | `30` | `MODERATE` TTL |
| `apicurio.http-caching.low-cacheability.max-age-seconds` | `10` | `LOW` TTL |
| `apicurio.http-caching.higher-quality-etags-enabled` | `true` | Include reference trees in ETags (more `MODERATE`, fewer `LOW`) |
| `apicurio.http-caching.opaque-etags-enabled` | `true` in `prod`, else `false` | Hash ETags |
| `apicurio.http-caching.extra-headers-enabled` | `true` | Send `X-Cache-Cacheability` |
| `apicurio.http-caching.vary-headers` | `Accept,Accept-Encoding,Authorization` | `Vary` |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| Entry point | [`HttpCaching`](../app/src/main/java/io/apicurio/registry/rest/cache/HttpCaching.java) | Evaluate the strategy, answer `If-None-Match`, add headers |
| Strategies | [`strategy`](../app/src/main/java/io/apicurio/registry/rest/cache/strategy/) | Level and ETag per endpoint |
| Headers, ETags | [`headers`](../app/src/main/java/io/apicurio/registry/rest/cache/headers/), [`etag`](../app/src/main/java/io/apicurio/registry/rest/cache/etag/) | Header formats, ETag building and hashing |
| Configuration | [`HttpCachingConfig`](../app/src/main/java/io/apicurio/registry/rest/cache/HttpCachingConfig.java) | Properties |
| Reference | [`rest/cache/README.md`](../app/src/main/java/io/apicurio/registry/rest/cache/README.md) | Endpoint by endpoint |

## Limitations and open work

1. No invalidation: deletes, imports, restores and GitOps changes need a manual purge of the affected
   coordinate URLs and, on GitOps and KubernetesOps, global-ID URLs
   ([#11183](https://github.com/Apicurio/apicurio-registry/issues/11183)).
2. Conditional requests never match with opaque ETags
   ([#11207](https://github.com/Apicurio/apicurio-registry/issues/11207)).
3. Only content and reference endpoints are cacheable; metadata, search and lists are not.
4. `Vary: Authorization` makes per-caller entries; there is no shared caching of authorized content.
5. The default TTLs are not based on measurements yet.

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| `Cache-Control` for every client | Not selected: clients would keep coordinates for days with no way to purge them |
| Active purging from the registry | Planned: needs cache discovery and complete URL mapping; TTLs first |
| Caching only by ID | Not selected: coordinates and branches are most of the traffic; their cost is #11183 |
| An in-process response cache | Not selected: every replica would cache separately; a shared surrogate absorbs traffic before it reaches any replica |

## Consequences

- **Benefits:** most reads answered without the registry; content IDs and hashes cacheable for ever; staleness bounded
  and explicit for moving targets; works with any number of replicas.
- **Costs:** a cache to run and configure; deletes need purges; revalidation savings lost in `prod`
  until #11207 is fixed.

## Example and verification

- **Example:** [`examples/http-caching`](../examples/http-caching/): `./demo.sh` checks the levels and
  headers, hits, misses and 304s, that writes and 404s are not cached, the stale content after a delete
  and recreate (and the purge that fixes it), and two KafkaSQL replicas in `prod` behind one Varnish
  (same ETags, the 304 issue, `branch=latest` bounded by the TTL); then the 93-check per-endpoint suite.
  CI runs it.
- **User documentation:** the [HTTP caching README](../app/src/main/java/io/apicurio/registry/rest/cache/README.md).

| Concern | Tests to read |
|---|---|
| Strategies | `EntityIdContentCacheStrategyTest`, `VersionContentCacheStrategyTest` (app) |
| References | `IdsContentReferencesCachingTest` (app) |
| Through Varnish | `examples/http-caching/test/run-tests.sh` |

## History

| Milestone | Result |
|---|---|
| HTTP caching phases 1 and 2 | Strategies, ETags, Varnish example and suite |
| This ADR | First written record; the example extended to replicas and staleness |
