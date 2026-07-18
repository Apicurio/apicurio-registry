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

### Factory Semantics Change (Compression)
When creating a client via `RegistryClientFactory`, the `middlewares` parameter behavior has changed. 
Previously, providing custom `middlewares` replaced the entire default middleware chain. 
Now, provided middlewares are appended to Kiota's default middleware chain (which includes compression support). 
If you pass an empty array, it will fall back to using the full default chain.
