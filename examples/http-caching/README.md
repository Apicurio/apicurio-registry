# HTTP caching

Apicurio Registry behind Varnish Cache: what the registry lets a cache keep, for how long, how it
revalidates, what never goes in the cache, what goes stale, and two replicas behind one cache.

- **Cacheability levels:** content by ID and concrete versions are cached for 10 days as immutable;
  `branch=latest`, drafts and dereferenced content for 30 seconds, then revalidated with the ETag.
- **What is safe:** writes and 404s are never cached; replicas give the same ETags.
- **What is not:** nothing invalidates the cache, so a deleted and recreated version is served stale
  until it is purged.
- **The full per-endpoint suite**, [`test/run-tests.sh`](test/run-tests.sh), runs at the end.

**New to this? Follow the [step-by-step guide](GUIDE.md).**

## Quick start

Requires Docker (or Podman) with Compose v2, `curl` and `jq`.

```bash
./demo.sh                 # start, check, remove everything
SKIP_SUITE=1 ./demo.sh    # without the full suite
KEEP=1 ./demo.sh          # leave the stack running
```

It ends with `All checks passed.`.

## Services

| Service | Port | Role |
|---|---|---|
| `registry` | 8080 (management 9000) | The registry, HTTP caching on, in-memory storage |
| `varnish` | 8081 (admin 6080) | Varnish 7.4 in front of it ([`varnish/default.vcl`](varnish/default.vcl)) |
| `ui` | 8888 | The console, through Varnish |
| `kafka`, `registry-a`, `registry-b`, `varnish-cluster` | 8082, 8083, 8084 | Profile `cluster`: two KafkaSQL replicas, prod profile, behind one Varnish ([`varnish/cluster.vcl`](varnish/cluster.vcl)) |

Every port can be changed: `REGISTRY_PORT`, `MANAGEMENT_PORT`, `VARNISH_PORT`, `VARNISH_ADMIN_PORT`,
`UI_PORT`, `REGISTRY_A_PORT`, `REGISTRY_B_PORT`, `VARNISH_CLUSTER_PORT`.

## Files

| File | Purpose |
|------|---------|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough, operating Varnish, presenter notes |
| [`docker-compose.yaml`](docker-compose.yaml) | The stack, and the `cluster` profile |
| [`docker-compose-dev.yaml`](docker-compose-dev.yaml) | Varnish and the console in front of a registry in Quarkus dev mode |
| [`varnish/`](varnish/) | The VCL: one backend, two replicas, dev mode |
| [`test/`](test/) | The per-endpoint test suite and its rules ([`test/README.md`](test/README.md)) |
| [`demo.sh`](demo.sh) | The walkthrough's checks, then the suite |
| [`ci.env`](ci.env) | CI settings |

## Notes

- **Known issues**, shown by the demo:
  - [#11183](https://github.com/Apicurio/apicurio-registry/issues/11183): content by coordinates is
    cached as immutable for 10 days, so a deleted and recreated version is served stale.
- **Local only:** the VCL allows `PURGE` from anyone and exposes debug headers; see *Operating
  Varnish* in the guide before using it elsewhere.

## Learn more

- [HTTP caching in Apicurio Registry](../../app/src/main/java/io/apicurio/registry/rest/cache/README.md):
  headers, endpoints and configuration.
- [ADR-0016](../../adr/0016-http-caching.md): the design, and what is guaranteed.
