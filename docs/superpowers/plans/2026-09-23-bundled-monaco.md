# Build-integrated Lazy Monaco Implementation Plan

> **For agentic workers:** Use the `executing-plans` skill to implement this plan task-by-task after
> design approval. Use subagents only when explicitly authorized. Checkboxes track execution.

**Goal:** Serve Monaco entirely from the UI build while loading it only when an editor is needed.

**Architecture:** Lightweight local React wrappers defer a shared Monaco runtime import. That runtime
configures Vite-built workers and the shared React loader before any third-party editor mounts.
PatternFly remains behind the same gate, preserving its existing behavior.

**Tech Stack:** React 19, TypeScript, Vite 7, Monaco 0.55.1, `@monaco-editor/react` 4.7.0,
PatternFly 6.6.1, Vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-23-bundled-monaco-design.md`

## Global constraints

- Preserve existing language support, callbacks, themes, model lifecycle, and editor options.
- No Monaco-specific runtime URL, AMD asset-copy step, CDN fallback, or eager startup initialization.
- All eight consumers must migrate, including JSON Schema examples and PatternFly diagnostic editors.
- `base: "./"` stays in place; validate the same output under `/` and `/registry/`.
- Keep loading promise ownership separate from individual component lifecycle.
- The slow-load threshold is 30 seconds; it displays guidance without cancelling initialization.
- No automatic page reloads or claims of transparent module-import retry.
- Use type-only imports outside the lazy implementations and enforce the import boundary with lint.
- Tests must check behavior and specific results, not just existence or spinner disappearance.
- Use existing Verify jobs and their Verification Gate rather than a standalone workflow.
- Run the project's test-quality review on new tests before submission.
- No commits or PR changes unless requested; any requested commits require DCO sign-off.

## Task 1: Establish a production baseline and regression fixtures

**Files:**
- Add `ui/tests/specs/monaco.spec.ts`.
- Add `ui/tests/specs/data/monaco-content.ts` for small JSON, Protobuf, and GraphQL text fixtures.
- Reuse the current UI image build and `REGISTRY_UI_URL` convention.

**Interfaces:** Browser tests use the deployed UI URL and the local backend API. Each test creates
and deletes its own uniquely named artifacts; it does not depend on `explore.spec.ts` execution order.

- [ ] Confirm a clean worktree and the actual target branch; record the baseline revision.
- [ ] Install the locked dependencies in a suitable isolated workspace. Record Node/npm versions and
  resolve local installation drift before using results as a baseline.
- [ ] Build the unmodified UI through the existing build/package/image path.
- [ ] Add a cold-context network observer before navigation. Allow only the configured local UI/API
  origins; record and abort external requests, then assert the recorded list is empty after an
  editor operation completes. Disable service workers in these test contexts.
- [ ] Create an artifact through the API and open its content tab with external access blocked.
  Assert a specific content token is rendered in the editor and capture the current failing behavior.
- [ ] Add separate fresh-context cases for a first-use diff and a first-use diagnostic editor.
  Trigger diagnostic state by fulfilling the relevant API request with a fixed error fixture.
- [ ] Capture initial-route and first-editor network transfers, build time, and output sizes.
  The baseline may still obtain Monaco externally when measuring normal connected behavior; record
  those transfers too so first-party and CDN bytes are not compared misleadingly.

Network enforcement should have this shape, using parsed origins rather than host substring matches:

```ts
const externalRequests: string[] = [];
await context.route("**/*", async route => {
    const url: URL = new URL(route.request().url());
    if ((url.protocol === "http:" || url.protocol === "https:") &&
        !allowedOrigins.has(url.origin)) {
        externalRequests.push(url.href);
        await route.abort();
        return;
    }
    await route.continue();
});
// Complete an actual editor operation before checking the accumulated requests.
expect(externalRequests).toEqual([]);
```

Observe dedicated worker URLs and errors separately. Browser routing alone is not proof that all
worker-internal networking was intercepted; use local worker assertions and, where needed, the test
container's network restrictions. The test must fail if Monaco logs a worker fallback warning.

**Verification:** From `ui/tests`, run `npm test -- monaco.spec.ts --project=chromium` against the
baseline production image. The offline-rendering cases must expose the current CDN failure.

## Task 2: Add the shared bundled runtime

**Files:**
- Add `ui/ui-app/src/app/components/codeEditor/monacoRuntime.ts`.
- Add `ui/ui-app/src/app/components/codeEditor/loadMonacoRuntime.ts`.
- Add corresponding `monacoRuntime.test.ts` and `loadMonacoRuntime.test.ts`.
- Add `ui/ui-app/src/vite-env.d.ts`.
- Modify `ui/ui-app/package.json` and its lockfile.
- Modify type imports in `src/editors/{registerLanguages,ProtobufLanguage,GraphQLLanguage}.ts`.

**Interfaces:**
- `initializeMonaco(): Promise<void>` configures the heavy runtime.
- Runtime exports `Editor` and `DiffEditor` from the upstream React wrapper.
- `loadMonacoRuntime(): Promise<typeof import("./monacoRuntime")>` caches initialization.

- [ ] Add explicit pinned runtime dependencies for `monaco-editor` and `@monaco-editor/react`;
  remove duplicate declarations from devDependencies. Do not combine this with dependency upgrades.
- [ ] Add the Vite declarations: `/// <reference types="vite/client" />`.
- [ ] Write tests with a deferred initialization promise. Two calls to `loadMonacoRuntime()` must
  return the same promise and invoke initialization once; neither may resolve before initialization.
- [ ] Test initialization rejection and verify it never triggers a fallback loader configuration.
- [ ] Implement the lightweight loader without a top-level heavy import:

```ts
type MonacoRuntime = typeof import("./monacoRuntime");
let runtimePromise: Promise<MonacoRuntime> | undefined;

/** Loads and initializes the shared bundled editor runtime on first use. */
export function loadMonacoRuntime(): Promise<MonacoRuntime> {
    runtimePromise ??= import("./monacoRuntime").then(async runtime => {
        await runtime.initializeMonaco();
        return runtime;
    });
    return runtimePromise;
}
```

- [ ] In `monacoRuntime.ts`, import the full Monaco ESM API and the React wrapper's loader.
  Import the five worker entry points listed in the spec using Vite `?worker` imports.
- [ ] Install `self.MonacoEnvironment.getWorker` with the exact mapping in the spec. Preserve other
  existing environment fields via object spread. Construct a new worker only when requested.
- [ ] Register custom languages, call `loader.config({ monaco })`, and await `loader.init()`.
  Treat this module as internal; all callers go through the cached lightweight loader.
- [ ] Test every worker label and alias with fake constructor functions. Assert the chosen
  constructor, no worker construction at import/initialization time, and distinct returned workers.
- [ ] Test ordering: environment and custom language registration precede loader initialization.
  Verify the actual configured Monaco object is the imported object.
- [ ] Run focused Vitest tests, then TypeScript/Vite build. Inspect emitted worker entry points and
  any duplicate native-fallback worker outputs from Monaco 0.55.1.

Example concurrency assertions, with the runtime module mocked before importing the lightweight loader:

```ts
const first = loadMonacoRuntime();
const second = loadMonacoRuntime();
expect(first).toBe(second);
await vi.waitFor(() => expect(initializeMonaco).toHaveBeenCalledTimes(1));
let resolved = false;
void first.then(() => { resolved = true; });
expect(resolved).toBe(false);
finishInitialization();
await expect(first).resolves.toBe(runtimeModule);
```

Use a module reset between these tests and avoid concurrent tests mutating the same module registry.

**Verification:** From `ui/ui-app`, run these commands:

```bash
npm test -- src/app/components/codeEditor/loadMonacoRuntime.test.ts \
    src/app/components/codeEditor/monacoRuntime.test.ts
npm run build
```

These tests validate coordination; real workers are validated in Task 5.

## Task 3: Implement the local lazy components and failure presentation

**Files:**
- Add `ui/ui-app/src/app/components/codeEditor/RegistryEditors.tsx`.
- Add `ui/ui-app/src/app/components/codeEditor/PatternFlyEditorAdapter.tsx`.
- Add `ui/ui-app/src/app/components/codeEditor/EditorLoadBoundary.tsx` and `.css`.
- Add `RegistryEditors.test.tsx` and `EditorLoadBoundary.test.tsx` alongside them.
- Add DOM-test dependencies in `ui/ui-app/package.json` and its lockfile.

**Interfaces:**
- `RegistryCodeEditor(props: EditorProps)`.
- `RegistryDiffEditor(props: DiffEditorProps)`.
- `RegistryPatternFlyCodeEditor(props: RegistryPatternFlyCodeEditorProps)`.
- The PatternFly facade uses this type-only language adaptation:

```ts
import type { CodeEditorProps, Language } from "@patternfly/react-code-editor";

export type RegistryPatternFlyCodeEditorProps = Omit<CodeEditorProps, "language"> & {
    language?: `${Language}`;
};
```

- `EditorLoadBoundary` accepts children, fallback dimensions, and optional diagnostic fallback text.

- [ ] Add compatible pinned `jsdom` and `@testing-library/react` dev dependencies. Use per-file
  `// @vitest-environment jsdom` directives so existing Node tests remain in their current environment.
- [ ] Write a real React render test that holds runtime initialization pending and verifies no
  mocked underlying editor mounts until it resolves. Include simultaneous regular/diff/PF consumers.
- [ ] Implement module-scope lazy components. Do not declare `lazy()` inside a render function.

```tsx
const LazyEditor = lazy(async () => {
    const runtime = await loadMonacoRuntime();
    return { default: runtime.Editor };
});

const LazyDiffEditor = lazy(async () => {
    const runtime = await loadMonacoRuntime();
    return { default: runtime.DiffEditor };
});

const LazyPatternFlyEditor = lazy(async () => {
    const [, adapter] = await Promise.all([
        loadMonacoRuntime(),
        import("./PatternFlyEditorAdapter")
    ]);
    return { default: adapter.PatternFlyEditorAdapter };
});
```

- [ ] Wrap each lazy component in `EditorLoadBoundary`, which owns local Suspense and a local React
  error boundary. Forward all editor props. The gate must finish before mounting third-party code.
- [ ] Use type-only upstream imports in the facade. Keep the actual PatternFly import in its adapter.
  Convert the literal language to `Language | undefined` only within that adapter.
- [ ] Add an accessible pending status and the 30-second slow-load guidance. Clear its timer on
  unmount. Continue waiting and recover normally when the import eventually succeeds.
- [ ] Render a generic local error alert and explicit reload button after rejection. Log technical
  errors using the existing logger. For PatternFly diagnostics, retain `code` as escaped text in a
  bounded `pre` so details remain readable when the editor itself fails.
- [ ] Preserve loading dimensions without adding a layout-changing success wrapper. Handle `sizeToFit`
  with a minimum fallback height rather than an invalid CSS height. Preserve caller `loading` content
  for the normal pending phase when supplied, but always show the slow-load guidance after 30 seconds.
- [ ] Test the following with React Testing Library, using `act` and controllable promises:
  pending mounts, rejection, slow-load notice, late success, unmount cleanup, changed props while
  pending, callback forwarding, diagnostic text escaping, and StrictMode initialization reuse.
- [ ] Assert a sibling navigation/control remains usable after local failure. Do not use the existing
  full-page `ErrorBoundary` as the editor fallback.

Example behavioral test sequence:

```tsx
const view = render(<RegistryCodeEditor value="before" />);
expect(screen.getByRole("status")).toHaveTextContent("Loading code editor");
expect(underlyingEditorMount).not.toHaveBeenCalled();
view.rerender(<RegistryCodeEditor value="after" />);
await act(async () => { finishInitialization(); });
expect(screen.getByTestId("underlying-editor")).toHaveTextContent("after");
```

The fake underlying editor renders its received value as DOM text; it does not reproduce Monaco.
Use real Monaco only in browser tests. Reset lazy-module caches between failure-case tests by importing
the facade in isolated modules, while maintaining a single React instance.

**Verification:** From `ui/ui-app`, run `npm test -- src/app/components/codeEditor` and `npm run lint`.

## Task 4: Migrate every consumer and enforce the boundary

**Files:** All eight consumer paths in the spec; `ui/ui-app/eslint.config.js`; custom language helpers.
When rebasing PR #9764, also edit its `main.tsx`, config service, package scripts, `.gitignore`, config
generator, and Monaco-specific documentation additions.

**Interfaces:** Consumers keep their current upstream props and callbacks through the new wrappers.

- [ ] Replace four regular editor imports with `RegistryCodeEditor` and the diff import with
  `RegistryDiffEditor`. Preserve options and callback implementations.
- [ ] Replace the three PatternFly imports with `RegistryPatternFlyCodeEditor`. Replace `Language.json`
  with `"json"`; preserve PF themes, dimensions, read-only settings, controls, and mount callbacks.
- [ ] Remove per-consumer custom-language `beforeMount` callbacks now handled by the runtime.
- [ ] Convert remaining Monaco type imports to explicit `import type`. Replace type import aliases
  with ordinary type aliases when necessary for clear type-only module dependencies.
- [ ] Replace the two `monaco.editor.getModels()[0]` uses with the mounted editor's `getModel()`.
- [ ] Audit tab/modal conditionals so hiding an unused editor does not eagerly mount it.
- [ ] Add ESLint `no-restricted-imports` rules for runtime imports of `monaco-editor`, its subpaths,
  `@monaco-editor/react`, `@monaco-editor/loader`, and `@patternfly/react-code-editor` outside the two
  lazy implementation modules. Allow type imports and focused integration tests.
- [ ] Verify ESLint also catches named imports and re-exports; include an explicit restriction for
  dynamic-import expressions outside the integration if the selected rule does not cover them.
- [ ] If working on the PR branch, remove Monaco copy hooks, `public/vs` ignore entry, startup
  configuration, `REGISTRY_MONACO_EDITOR_URL`, and its docs. Preserve unrelated Kiota preparation.
- [ ] If working from main, simply omit those PR-only additions; do not create then remove them.

Migration examples:

```tsx
import { RegistryCodeEditor } from "@app/components/codeEditor/RegistryEditors";
import type { editor } from "monaco-editor";
type IStandaloneCodeEditor = editor.IStandaloneCodeEditor;

// Preserve the existing props when replacing <Editor> with <RegistryCodeEditor>.
// PF callbacks must address their own model:
onEditorDidMount={(editor) => {
    editor.layout();
    editor.getModel()?.updateOptions({ tabSize: 4 });
}}
```

**Verification:** Run `npm run lint`, `npm test`, and `npm run build` in `ui/ui-app`. Re-scan source
imports and assert that no ungated consumer remains. Verify eight migrations explicitly against the
spec table instead of relying solely on a substring count.

## Task 5: Prove real workers, language parity, and lazy delivery

**Files:** Extend `ui/tests/specs/monaco.spec.ts` and its fixtures. Add small test-only observation
helpers under `ui/tests/specs/helpers/monaco.ts` if needed. Do not add a production global test API.

**Interfaces:** Browser actions use application routes and Monaco's visible commands. Test helpers can
observe Worker construction/errors using `context.addInitScript` without changing the production API.

- [ ] Run content, diff, and PF-first-use cases with all external traffic blocked. Assert specific
  visible content and zero attempted external Monaco URLs in each fresh browser context.
- [ ] On an editable JSON artifact, enter `{"answer":42}` and invoke Monaco's Format Document action
  through its command palette/context menu. Assert the resulting model text through the editor UI
  or saved artifact content. Require a local JSON Worker instance, successful message exchange, and
  no worker fallback/error. Do not use the content viewer's JSON.stringify output as worker evidence.
- [ ] Exercise diff computation with one changed line; assert the changed line and inline/side-by-side
  toggle behavior. Check that a local editor worker runs, rather than just asserting two panes exist.
- [ ] Open Protobuf and GraphQL content and assert expected keyword token styling, preserving the
  custom tokenizers. Check JSON/YAML switching and the JSON Schema generated-example viewer.
- [ ] Check editing/onChange persistence and mount callbacks. Open two editors together and verify
  their values/options do not cross-contaminate after lazy initialization.
- [ ] Start on `/explore` with a cold browser context. After a positive page-ready assertion, verify
  no Monaco runtime chunk or worker was requested. Open the first editor, observe the runtime request,
  then reopen an editor and verify no duplicate runtime initialization/download.
- [ ] For robust chunk identification, build verification output with `vite build --manifest
  --sourcemap`. Inspect manifest static/dynamic edges and source-map source lists; identify actual
  emitted Monaco-bearing files instead of assuming chunk filenames or grepping the CDN string.
- [ ] Confirm no Monaco implementation is reachable through the initial static import graph. The
  default CDN URL may remain as unused loader text; network behavior, not string absence, is decisive.
- [ ] Abort a known lazy runtime chunk request in a fresh context and assert the local error alert,
  readable diagnostic fallback where relevant, usable navigation, and no CDN retry.
- [ ] Record worker output duplication, transfer sizes, first-use latency, and build resource costs.
  Keep the full language import unless measurements justify a separately reviewed reduction.

**Verification:** From `ui/tests`, run `npm test -- monaco.spec.ts --project=chromium` against the
production image, followed by the existing suite. Dev-server success is supplementary evidence only.

## Task 6: Validate prefixed deployment and integrate regression coverage in CI

**Files:**
- Modify `.github/workflows/verify-extras.yaml` within the existing `ui-e2e-tests` job.
- Add `ui/tests/fixtures/monaco-prefix-proxy.conf`.
- Update `ui/tests/DOCKER-TESTING.md` and `ui/README.md`.

**Interfaces:** The existing root suite runs at port 8888. A second instance of the same UI image uses
`REGISTRY_CONTEXT_PATH=/registry/`; a test proxy exposes it under `/registry/` and strips that prefix
before forwarding. The new Monaco spec accepts its URL via existing `REGISTRY_UI_URL`.

- [ ] Add a test proxy configuration that redirects `/registry` to `/registry/` and forwards
  `/registry/` to the root of the prefixed UI container. Use the real image startup scripts to set
  the base href and config. The backend remains a permitted local origin.
- [ ] Run the focused Monaco spec against the prefixed URL without rebuilding the UI. Test both
  navigation from the landing route and direct navigation/refresh of a nested editor route.
- [ ] Assert editor chunks, worker scripts, styles, and fonts load successfully from the expected
  prefix; reject HTML responses for JavaScript/worker URLs, even if the HTTP status is 200.
- [ ] Add readiness polling for both app/proxy deployments rather than fixed sleeps. Ensure test
  containers are cleaned up and logs collected on failure.
- [ ] Keep root and prefix Playwright report directories distinct so the second run cannot overwrite
  the first. Upload both through the existing report-artifact step.
- [ ] Confirm `verify-decide.yaml` still selects extras for UI changes and the existing Verification
  Gate includes extras. No new independent workflow is required.
- [ ] Document that Monaco follows normal built-asset deployment, requires no external CDN, and uses
  the canonical trailing-slash context path. Explain lazy first-use behavior and local failure UI.
- [ ] Document reproducible root/prefix test commands and the emitted-asset measurements.

Proxy routing should use the trailing slash on `proxy_pass` deliberately:

```nginx
location = /registry {
    return 301 /registry/;
}
location /registry/ {
    proxy_pass http://registry-ui-prefixed:8080/;
}
```

**Verification commands:**

| Directory | Command | Expected result |
| --- | --- | --- |
| `ui/ui-app` | `npm run lint` | Import boundary and existing lint checks pass |
| `ui/ui-app` | `npm test` | Existing and new unit/component tests pass |
| `ui/ui-app` | `npm run build` | TypeScript and Vite succeed; worker assets emitted |
| `ui` | `npm run build` | All UI applications build through normal orchestration |
| `ui` | `npm run package` | App chunks/workers included in the assembled UI |
| `ui` | `docker build -t apicurio-registry-ui:monaco-local .` | Normal UI image contains assets |
| `ui/tests` | `npm run lint` | Browser tests follow project conventions |
| `ui/tests` | `npm test -- monaco.spec.ts --project=chromium` | Root production cases pass |
| `ui/tests` | `REGISTRY_UI_URL=http://localhost:8889/registry npm test -- monaco.spec.ts --project=chromium` | Same image passes prefixed/deep-link cases |
| `ui/tests` | `npm test` | Existing root-deployment regression suite passes |

Run `/apicurio-test-quality` on the new/changed tests and resolve findings before submission. Check
`git diff --check` and review the final diff for PR-only config/copy leftovers and unrelated changes.

## Completion evidence

- [ ] Eight consumers use the local gate; lint prevents new direct runtime imports.
- [ ] External access blocked: regular, diff, and PatternFly first-use paths all work.
- [ ] Real JSON and editor workers execute successfully from local emitted assets.
- [ ] Cold non-editor routes do not request or execute Monaco implementation code.
- [ ] Loading failure stays local and does not trigger CDN fallback or automatic reload.
- [ ] Root, prefixed, and refreshed nested routes work with the same packaged image.
- [ ] Existing language support, model behavior, callbacks, and UI controls remain intact.
- [ ] Initial and first-use payload/performance measurements are recorded, not estimated.
- [ ] Unit, browser, build, lint, and test-quality checks pass in the existing gated CI path.
