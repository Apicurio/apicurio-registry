# Apicurio Registry UI

Apicurio Registry UI is a React based Single Page Application based on PatternFly 6.

## Requirements
This project requires Node.js `^20.19.0 || >=22.12.0` (the engine requirement of Vite 7) and npm 10 or later.
Prior to building this project make sure you have these applications installed.

## Development Scripts

Install development/build dependencies
`npm install`

Run a full build
`npm run build`

Initialize config.js
`./init-dev.sh`

Note: the init-dev.sh script just copies an appropriate file from configs/config-*.js to the right place.  You can 
either specify `local` or `3scale` (for example) as the argument to the script.  The choice depends on how you are 
running the back-end component.

Start the development server
`npm run dev`

Once the development server is running you can access the UI via http://localhost:8888

Note that you will need a registry back-end running for the UI to actually work.  The easiest way to do this is using 
docker, but you could also run the registry from maven or any other way you choose.  Here is how you do it with Docker:

`docker run -it -p 8080:8080 apicurio/apicurio-registry:latest-snapshot`

## Code Editor (Monaco)

Every code/diff editor in the UI (content viewing, diff comparisons, protobuf/draft editing, and
PatternFly's diagnostic `CodeEditor`) goes through the wrappers in
`src/app/components/codeEditor/RegistryEditors.tsx` (`RegistryCodeEditor`, `RegistryDiffEditor`,
`RegistryPatternFlyCodeEditor`). These lazily load a single, bundled Monaco runtime
(`src/app/components/codeEditor/monacoRuntime.ts`) on first use, so the app works on networks
without internet access (Monaco is never fetched from a CDN) and Monaco is not part of the initial
application bundle. Do not import `monaco-editor`, `@monaco-editor/react`, `@monaco-editor/loader`,
or `@patternfly/react-code-editor` directly outside of `monacoRuntime.ts` /
`PatternFlyEditorAdapter.tsx` — an ESLint rule enforces this (`import type` is still fine anywhere).
