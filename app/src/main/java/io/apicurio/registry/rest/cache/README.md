# HTTP Caching Overview

There are four categories of Apicurio Registry REST API endpoints with respect to HTTP caching:

## Highly Cacheable Endpoints

These are:

- `/ids/globalIds/*`
- `/ids/contentIds/*`
- `/ids/contentHashes/*`
- `/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/content` **[1] [2]**
- `/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/references` **[1] [2?]**
- `/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/references/graph` **[1?] [2?]**

1. Only if the version expression references a specific version, e.g. not `latest` nor  `branch=...`.
2. Only if the artifact version is not in a draft state.

These endpoints return the following HTTP headers:

- `Cache-Control: public, immutable, max-age=31536000` (1 year default)
- `Etag: <id>` (where `<id>` is global ID, content ID or hash)
- `Vary: Accept, Accept-Encoding` (for content endpoints)

Cache expiration can be set by configuration property `apicurio.http-caching.highly-cacheable.max-age-seconds` (default `31536000`).
If this property is set to `0`, caching for these endpoints is disabled.

Cache invalidation is not required.

## Moderately Cacheable Endpoints

> This is work in progress.

These are:

- `/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/content` **[1]**
- `/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/references` **[1]**
- `/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/references/graph` **[1?]**

**[1]** Only if the version expression **does not** reference a specific version, e.g. `latest` or  `branch=...`.

Cache expiration can be set by configuration property `apicurio.http-caching.moderately-cacheable.max-age-seconds` (default: `600`).
If this property is set to `0`, caching for these endpoints is disabled.

## Weakly Cacheable Endpoints

> This is work in progress.

## Non-Cacheable Endpoints

> This is work in progress.

## Unknown Cacheability Endpoints

> This is work in progress.

- `/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/render`

## TODO

- Schema resolver cache should use Etag headers.
