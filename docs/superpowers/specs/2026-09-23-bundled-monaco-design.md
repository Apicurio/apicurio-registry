# Build-integrated, lazy-loaded Monaco: analysis and proposed design

**Status:** Proposed for review; implementation and production measurements have not been performed.

**Evidence:** Repository `6e414d15e`; PR #9764 head `ae28ebd94c15482185f380b0262c2dc2368b28f9`.
Installed dependency source was inspected for Monaco 0.55.1, React wrapper 4.7.0, loader 1.7.0,
PatternFly code editor 6.6.1, and Vite 7.3.6. The local dependency tree has unrelated Vitest version
drift, so a clean dependency installation is required before implementation measurements.

## Recommendation

Bundle Monaco with Vite and defer its import until a local editor component is rendered. Introduce
three small, typed React wrappers for the existing regular, diff, and PatternFly editor APIs. All
three use one shared initialization promise and a local loading/error boundary.

This directly implements the proposed custom code-editor component as a lazy-loading gate. Retaining
three prop-compatible entry points avoids forcing fundamentally different editor APIs into a large
union of modes and options.

## What this solves

- Editor startup does not depend on jsDelivr or any other external Monaco asset host.
- Monaco, its styles, language modules, fonts, and workers participate in the normal Vite build.
- Users who remain on screens without editors do not download or execute the Monaco runtime.
- Concurrent editors share initialization without racing the default AMD/CDN loader.
- Import failures have a local, understandable failure state instead of a permanent spinner.
- Existing editor features, themes, callbacks, model ownership, and language support are preserved.

Here, "offline" means the Registry UI and API are reachable but external Internet access is blocked.
This does not add a service worker or promise operation when the Registry server itself is unreachable.

## Corrections to the earlier review

The copy-based solution is a legitimate minimal fix. Vite normally copies `public/` into `dist/`;
`ui/.scripts/package.cjs:7` copies the app output into the packaged UI, and `ui/Dockerfile:32` copies
that package into the image. Dockerfile changes are not intrinsically required for either approach.

Build integration catches unresolved imports and build failures. It does not prevent stale chunks,
bad proxy routing, missing deployed files, incorrect MIME types, or blocked workers at runtime.
Neither Vite nor hashed filenames automatically supplies CSP compliance or subresource integrity.

The durable benefits are explicit dependencies, managed asset URLs, cache-busted output, fewer
deployment settings, and a centralized integration boundary. Runtime deployment tests remain essential.

## Current implementation

### Consumers

All paths below are relative to `ui/ui-app/src/`.

| Consumer | Current component | Local replacement |
| --- | --- | --- |
| `editors/TextEditor.tsx` | React Monaco `Editor` | `RegistryCodeEditor` |
| `editors/ProtoEditor.tsx` | React Monaco `Editor` | `RegistryCodeEditor` |
| `app/pages/version/components/tabs/ContentTabContent.tsx` | React Monaco `Editor` | `RegistryCodeEditor` |
| `app/pages/version/components/tabs/visualizers/JsonSchemaVisualizer.tsx` | React Monaco `Editor` | `RegistryCodeEditor` |
| `app/components/common/DiffView.tsx` | React Monaco `DiffEditor` | `RegistryDiffEditor` |
| `app/components/errorPage/ErrorPage.tsx` | PatternFly `CodeEditor` | `RegistryPatternFlyCodeEditor` |
| `app/pages/version/components/tabs/ErrorTabContentState.tsx` | PatternFly `CodeEditor` | `RegistryPatternFlyCodeEditor` |
| `app/components/modals/GenerateClientModal.tsx` | PatternFly `CodeEditor` | `RegistryPatternFlyCodeEditor` |

The separate Angular designer under `ui/ui-editors` uses its own editor integration. It is outside
this Monaco migration. Do not conflate its existing `REGISTRY_EDITORS_URL` with Monaco asset loading.

### Loader behavior

`@monaco-editor/loader/lib/es/loader/index.js:43-69` checks for a configured Monaco instance during
its first `init()` call. Without one, it injects the AMD loader script. Its initialized flag and
wrapper promise are shared; configuring Monaco after initialization starts does not undo that request.

Consequently, neither a parent `useEffect` nor an unawaited dynamic import in `main.tsx` is a safe gate.
The actual third-party editor must not mount until the bundled instance has been supplied.

PatternFly imports `Editor` from `@monaco-editor/react`. The inspected dependency tree deduplicates
these consumers to the same wrapper and loader. PatternFly also installs its own themes and keyboard
behavior, so replacing it outright would introduce avoidable UI changes.

### Build and deployment

- `ui/ui-app/vite.config.mts:8` uses `base: "./"` and Vite's normal chunking.
- `index.html` has a base element, rewritten by `ui/.docker-scripts/update-base-href.cjs` at startup.
- `App.tsx` applies the runtime context path as the React Router basename.
- The nginx configuration assumes deployment routing will map the public prefix to the app root.
- UI unit tests use Vitest's Node environment; a DOM component-test environment is not installed.
- Existing Playwright tests run against production UI and backend images in `verify-extras.yaml`.
- UI changes already select the extras suite; its result participates in the Verification Gate.

## Options considered

| Approach | Assessment |
| --- | --- |
| Eager bundled Monaco initialized before app render | Correct ordering, but adds Monaco to startup for every route. Useful only as a short integration experiment. |
| Shared lazy runtime plus typed local wrappers | Recommended. One initialization boundary, preserves APIs, and defers cost until needed. |
| Replace every consumer with one new universal editor API | More translation and behavioral risk, especially for diffs and PatternFly. Unnecessary for this fix. |

## Architecture

```text
Non-editor routes
    -> lightweight Registry editor facade (no Monaco runtime import)

First rendered Registry editor
    -> local error boundary + Suspense
    -> shared loadMonacoRuntime() promise
        -> dynamic import monacoRuntime.ts
        -> install MonacoEnvironment worker factories
        -> register existing custom languages
        -> loader.config({ monaco })
        -> await loader.init()
    -> mount React Monaco Editor / DiffEditor
       or separately lazy-imported PatternFly CodeEditor

Later/concurrent editors
    -> reuse the same resolved/in-flight runtime promise

Language services / diff computation
    -> request Vite-built workers as needed
```

### Files and responsibilities

Put the integration in a new `codeEditor/` directory under the existing
`ui/ui-app/src/app/components/` hierarchy:

- `RegistryEditors.tsx`: public, prop-compatible components and module-scope `React.lazy` declarations.
- `loadMonacoRuntime.ts`: lightweight shared promise; dynamic import is inside the function.
- `monacoRuntime.ts`: heavy Monaco import, worker configuration, language registration, loader setup.
- `PatternFlyEditorAdapter.tsx`: heavy PatternFly import and local language-type adaptation.
- `EditorLoadBoundary.tsx`: local error boundary, Suspense, pending/slow/error presentation.
- `EditorLoadBoundary.css`: bounded fallback layout; no global editor styling changes.

Consumers import `RegistryEditors.tsx` directly. No barrel may statically re-export the heavy runtime
or the PatternFly adapter. Type-only imports are allowed and required for Monaco/PatternFly types
outside the integration implementations.

### Public components

- `RegistryCodeEditor` accepts the upstream `EditorProps` unchanged.
- `RegistryDiffEditor` accepts the upstream `DiffEditorProps` unchanged.
- `RegistryPatternFlyCodeEditor` preserves `CodeEditorProps`, except its language type also accepts
  the literal values of PatternFly's `Language` enum. This permits `language="json"` without importing
  a runtime enum from the PatternFly component module.

Use a template-literal type derived from a type-only `Language` import rather than duplicating the
language list. Convert that representation to the upstream enum type inside the lazy adapter.

Preserve upstream callbacks and defaults. The integration must not make all editors read-only,
introduce new global themes, add an unconditional `path`, or take over model disposal.

### Lazy initialization

`loadMonacoRuntime()` returns a cached promise resolving to the heavy runtime module only after its
`initializeMonaco(): Promise<void>` function succeeds. Each lazy component awaits that promise before
returning its component implementation to React. The PatternFly wrapper can load its adapter and
the runtime concurrently, but cannot render the adapter until both have resolved.

Initialization runs once per page module instance. Unmounting one editor does not cancel shared
initialization or dispose shared workers. Monaco and the upstream React wrappers retain their normal
worker/editor/model lifecycle responsibilities.

Do not import the heavy runtime or call `loadMonacoRuntime()` from `main.tsx`. Do not add automatic
idle prefetching in this change: it would weaken the "no Monaco until an editor is requested" contract.
Mount-based laziness is sufficient; visibility-based loading would require a separate product decision.
Check that hidden tabs and closed modals do not mount editors merely to hide them with CSS.

### Workers and language preservation

Start with the complete `monaco-editor` ESM entry point, not a hand-selected minimal editor API.
The installed entry point registers built-in languages and editor actions, and imports editor CSS.
Keep the existing custom Protobuf and GraphQL registrations and run them during shared initialization.
Remove redundant `beforeMount={registerCustomLanguages}` calls after centralization.

Configure these Vite `?worker` entry points using a typed `MonacoEnvironment.getWorker`:

| Labels | Worker entry relative to `monaco-editor/esm/vs/` |
| --- | --- |
| `json` | `language/json/json.worker.js?worker` |
| `css`, `scss`, `less` | `language/css/css.worker.js?worker` |
| `html`, `handlebars`, `razor` | `language/html/html.worker.js?worker` |
| `typescript`, `javascript` | `language/typescript/ts.worker.js?worker` |
| Other labels | `editor/editor.worker.js?worker` |

Imports provide constructors, not eagerly started workers. Construct workers only inside `getWorker`;
return a fresh worker to the requesting Monaco manager, rather than caching one Worker per label.
Do not use inline/blob workers or hard-coded deployment paths. Add Vite client type declarations.

YAML, XML, Protobuf, and GraphQL tokenization do not imply dedicated language-service workers. Preserve
their existing highlighting; do not add a YAML language server or schema-download feature here.
Monaco's inspected JSON defaults have `enableSchemaRequest: false`; preserve that default.

Monaco 0.55.1 also includes native `new Worker(new URL(..., import.meta.url))` fallbacks in language
services. Explicit `getWorker` takes precedence. Inspect emitted assets for duplicate worker output;
do not assume one output file per explicit import. Keep explicit mapping for predictable integration
initially; change it only if build/runtime evidence justifies doing so.

### Loading and failure behavior

- Pending: show an accessible "Loading code editor…" status in the requested editor space.
- Still pending after 30 seconds: replace the short status with "The code editor is taking longer
  than expected. You can keep waiting or reload the page." Keep the import alive and permit recovery.
- Rejected import/initialization or synchronous render failure: show a local "Unable to load the
  code editor" alert with an explicit reload action. Keep surrounding navigation usable.
- Never fall back to the CDN. Never automatically reload a page that may contain unsaved work.
- Do not offer a misleading retry that merely resets React state: `React.lazy` caches rejected
  promises, and failed module evaluation can also remain cached by the browser.
- Error details are logged through the existing logger; the displayed message is generic.
- Preserve the current diagnostic text in a plain, escaped `pre` for PatternFly diagnostic consumers
  when loading fails. Do not route editor failures through `ErrorPage`, which itself uses an editor.

The slow-load timer is per pending fallback and is cleared on unmount. It is a UX notice, not an
attempt to abort a JavaScript import. This avoids treating a slow but recoverable local network as
a permanent failure.

A React boundary does not catch later asynchronous worker failures. Browser tests must verify real
worker operations and reject worker errors/fallback warnings. A general worker-health monitor and
runtime recovery system would be a separate feature, rather than a prerequisite for removing the CDN.

### Layout and model correctness

Preserve supplied width/height in the pending and error UI. Handle numeric dimensions, `100%`, and
PatternFly's `sizeToFit` separately; never emit `height: sizeToFit` as CSS. Avoid an extra permanent
wrapper that breaks existing full-height flex layouts. Use the local boundary as a React-only wrapper
where possible, with sized DOM only for fallback states.

The existing callbacks in `ErrorPage.tsx` and `GenerateClientModal.tsx` update `getModels()[0]`.
Change those callbacks to `editor.getModel()?.updateOptions({ tabSize: 4 })` during migration. An
editor's mount order is not a reliable way to choose its model, particularly with concurrent lazy mounts.

## Deployment and caching

Keep `base: "./"`. Vite's inspected implementation resolves relative worker and preload URLs against
the importing module's URL. This is promising for relocating the same build under a proxy prefix,
but the HTML entry point and server routing still need to work first.

Test the same built output at `/` and `/registry/`, including a direct nested-route load and refresh.
Use a prefix-stripping reverse proxy for the latter and the actual startup base-href rewrite.
The existing `/registry` versus `/registry/` base-href behavior is a broader deployment concern:
do not claim bundling automatically fixes it. Document the canonical trailing-slash context path;
any normalization change must cover HTML base, routing, and sibling asset URLs together.

Hashed chunks improve cache invalidation, but stale HTML referencing removed assets can still fail.
Keep deployment atomicity and HTML caching behavior in the verification checklist. Retain existing
security policy behavior; verify same-origin workers without claiming general CSP compliance.

## Performance expectations and measurements

The initial non-editor route must have no Monaco implementation/worker payload in its static module
graph and make no Monaco chunk or worker requests. A lightweight facade is an acceptable initial cost.
First editor use downloads the runtime and required language resources; subsequent use reuses them.

Record before/after initial transferred JavaScript, editor-first-use transferred JavaScript, output
sizes, first-editor readiness time, worker requests, build duration, and peak build memory. Report
cold and warm cache separately. Do not choose arbitrary numeric budgets without a baseline.

Expect a larger first-party build and possibly longer CI builds; the complete Monaco distribution
contains large language services. Optimize selected imports only after measurement and explicit
language-parity checks. Avoid manual chunks that accidentally pull Monaco into an eager vendor chunk.

## Verification requirements

1. Unit tests prove one shared initialization under concurrent requests and rejection propagation.
2. Worker mapping tests cover every label alias and the default, without starting actual workers.
3. Real React component tests prove the third-party editor cannot mount before initialization.
4. Component tests cover updated props while pending, StrictMode, unmount, local failure isolation,
   slow-load recovery, and callback forwarding. Use a DOM environment; do not mock React's hooks.
5. Production browser tests block external network access and assert zero attempted external Monaco
   requests, not merely that blocked requests fail harmlessly.
6. A fresh page exercises a regular editor, diff editor, and PatternFly editor as the first consumer.
7. JSON formatting or diagnostics produces a specific result using a successfully running local worker.
   Merely observing formatted content is insufficient: `ContentTabContent` formats JSON itself.
8. Existing Protobuf/GraphQL highlighting, JSON/YAML switching, editing, themes, and diff controls work.
9. The same artifact passes root, prefixed, and direct deep-link deployment cases.
10. Fault injection for a lazy chunk yields the local failure UI without an external retry.
11. Existing Verify → Decide → Verification Gate jobs run the new checks.

## Approval and implementation

This is a proposed design and implementation plan, not a claim that the integration is already built
or benchmarked. Approve the shared lazy runtime and three-wrapper design before implementation.
See `../plans/2026-09-23-bundled-monaco.md` for implementation tasks and acceptance criteria.
