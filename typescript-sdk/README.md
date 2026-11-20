# Apicurio Registry Typescript SDK
This library provides an SDK for Apicurio Registry for use in Node.js and front-end (UI)
applications.  It is written in Typescript using Kiota to generate the REST client.

## Building
Use standard Node/NPM tooling to build the code in this library.

```
npm install
npm run generate-sources
npm run lint
npm run build
```

## Release Notes

### Behavioral Change: Requests Are Now Compressed By Default

`RegistryClientFactory.createRegistryClient()` now builds its default middleware chain from
Kiota's `MiddlewareFactory.getPerformanceMiddlewares()`, which adds a `CompressionHandler` on
top of the previous default chain (retry, redirect, parameter decoding, user agent, headers
inspection). As a result, **outgoing request bodies are now gzip-compressed
(`Content-Encoding: gzip`) by default**, matching the server's `enable-decompression` support.

This is a behavioral change for existing callers of `createRegistryClient()`: any custom
`middlewares` you pass are still appended after the defaults (this was already true before this
change — passing middlewares never replaced the defaults), but the defaults themselves now
compress request bodies where they previously did not.

If you need to opt out (e.g. to fully control the middleware chain yourself, or to talk to a
server that doesn't support decompression), pass `useDefaultMiddlewares = false` as the fourth
argument:

```ts
RegistryClientFactory.createRegistryClient(baseUrl, authProvider, customMiddlewares, false);
```

With `useDefaultMiddlewares = false`, `customMiddlewares` is used as-is with no defaults added.
