# ADR-0027: Web console and console plugin

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-19.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** the web console ([`ui/`](../ui/)) and the OpenShift console plugin
  ([`console-plugin/`](../console-plugin/)); open work in
  [#10910](https://github.com/Apicurio/apicurio-registry/issues/10910) and
  [#10172](https://github.com/Apicurio/apicurio-registry/issues/10172).
- **Scope:** How the registry's browser console is built, packaged, configured and served, how it
  follows the server's settings and calls the REST API, and how the operator adds an OpenShift
  console plugin. Not covered: how the server authenticates callers
  ([ADR-0009](0009-authentication.md)), the TypeScript SDK itself
  ([ADR-0022](0022-client-sdks-and-cli.md)), the operator's other resources
  ([ADR-0019](0019-operator.md)), and the server's configuration model
  ([ADR-0025](0025-configuration.md)).

## Reading this document

- Start with [the decision](#decision).
- For deployment: [configuration](#configuration) and the
  [user documentation](../docs/modules/ROOT/pages/getting-started/assembly-managing-registry-artifacts-ui.adoc).
- For a running system: the [authentication example](../examples/authentication/) (console with
  OIDC login) or the [operator example](../examples/operator/).
- For code changes: the [source map](#source-map) and [verification](#example-and-verification).

## Context

People browse, create and govern artifacts in a browser as much as through the API. The console
has to show what the server allows (deletion, read-only storage, role management, experimental
features) without its own copy of those rules, log users in with the same identity provider the
API trusts, and be deployable next to the API on Docker, Kubernetes and OpenShift. On OpenShift,
administrators also expect registry instances to appear in the OpenShift Console itself.

## Decision

1. **The console is a separate single-page application with its own build and image.** `ui/` is an
   npm project, not a Maven module: `ui-app` (React 19, PatternFly 6, Vite), `ui-docs` (API
   documentation viewer) and `ui-editors` (Studio editors) are built separately and packaged into one
   `dist/` with `docs/` and `editors/` subfolders ([`package.cjs`](../ui/.scripts/package.cjs)).
   The [`Dockerfile`](../ui/Dockerfile) serves `dist/` with nginx (UBI 10) as
   `apicurio/apicurio-registry-ui`, published to Docker Hub and Quay by `release-images.yaml`. The
   registry server does not serve the console.
2. **Configuration is a `config.js` written at container start.** The image's
   [`entrypoint.sh`](../ui/.docker-scripts/entrypoint.sh) runs
   [`create-config.cjs`](../ui/.docker-scripts/create-config.cjs), which turns `REGISTRY_*`
   environment variables into a global `ApicurioRegistryConfig` in `/opt/app-root/src/config.js`,
   and [`update-base-href.cjs`](../ui/.docker-scripts/update-base-href.cjs), which sets
   `<base href>` from `REGISTRY_CONTEXT_PATH`. nginx serves `config.js` with `no-cache` and falls
   back to `index.html` for client-side routes, on port 8080 (HTTP) and 1337 (HTTPS, with a
   self-signed certificate generated at image build).
3. **The server's settings are fetched and merged before the app renders.** `main.tsx` calls
   `fetchAndMergeConfigs()`, which reads `GET /apis/registry/v3/system/uiConfig` (unauthenticated)
   from the API URL in `config.js`, then overrides the result with any property set locally. The API
   URL always comes from the local config. If the call fails, the console runs on its local config
   only and logs that it did so.
4. **Feature flags are computed by the server.** `SystemResourceImpl.getUIConfig()` derives the
   flags from the server's own settings, so the console hides what the server would reject:

   | Flag | Derived from |
   |---|---|
   | `readOnly`, `readOnlyReason`, `storageName` | `apicurio.ui.features.read-only.enabled`, or read-only storage (`readOnlyReason: storage`) |
   | `deleteGroup`, `deleteArtifact`, `deleteVersion` | The `apicurio.rest.deletion.*` settings, and not read-only |
   | `draftMutability` | Artifact version mutability |
   | `roleManagement` | RBAC enabled with the `application` role source |
   | `agents` | Experimental features and `apicurio.ui.features.agents.enabled` |
   | `searchIndex` | Elasticsearch search enabled |
   | `mcpImport` | MCP tool import available and enabled, and not read-only |
   | `breadcrumbs`, `settings`, `urlImportMaxContentLength` | `apicurio.ui.features.*` |

5. **The console calls the API through the generated TypeScript SDK.** `ui-app` depends on
   `@apicurio/apicurio-registry-sdk` as an npm workspace of [`typescript-sdk/`](../typescript-sdk/)
   (built from source by `npm install` in `ui/`), and creates its client in
   [`rest.utils.ts`](../ui/ui-app/src/utils/rest.utils.ts). Some calls use axios instead: `uiConfig`, import
   from URL, the data contract endpoints and prompt rendering. Every request carries the user's credentials: a bearer token for OIDC, a
   Basic header for `basic`.
6. **Login is OIDC in the browser.** The auth type and options come from `uiConfig`
   (`quarkus.oidc.auth-server-url`, `apicurio.ui.auth.oidc.*`) or `REGISTRY_AUTH_*`.
   `ApplicationAuth` from `@apitomy/common-ui-components` (built on `oidc-client-ts`) runs the login;
   a relative `redirectUri` or `logoutUrl` is made absolute against the page origin. With OIDC, a 401
   on a request made through the SDK triggers one shared token refresh and a retry; if the refresh
   fails, the console asks the user to log in again (`RefreshOn401Handler`, installed only for OIDC
   in `createRegistryClient`, and `useReauthenticationService`). The axios calls (data contracts,
   prompt rendering) and Basic authentication do not get this recovery: a 401 is returned as an
   error.
7. **The OpenShift console plugin is opt-in through the operator.** When
   `spec.consolePlugin.enabled` is `true` and the plugin image is configured on the operator
   (`REGISTRY_CONSOLE_PLUGIN_IMAGE`), the operator creates the plugin Deployment and Service
   (`ActivationConditions`); this check does not look at the cluster type. Only the cluster-scoped
   `ConsolePlugin` named `<namespace>-<name>-console-plugin` additionally requires the
   `console.openshift.io` API (`ConsolePluginManager`); without it, or when the plugin is disabled,
   the operator deletes that `ConsolePlugin`.
8. **The plugin lists instances and embeds the console, it does not reimplement it.** The plugin
   ([`console-extensions.json`](../console-plugin/console-extensions.json)) adds an *Apicurio*
   section to the administrator perspective with list and detail pages for `ApicurioRegistry3`, and
   a tab that loads the registry console in an iframe from the CR's UI (or app) ingress host. When
   the CR enables authentication, the tab shows a link to open the console directly instead.

### Why a separate image

A static bundle in nginx scales, restarts and is upgraded independently of the API, can sit on its
own host or context path, and needs no JVM. The cost is a second URL to configure: the browser must
reach the API at `REGISTRY_API_URL`, and an OIDC client must accept the console's address as a
redirect URI.

## The console plugin

| Part | Behaviour |
|---|---|
| Frontend | React 17 and PatternFly 5, built with the OpenShift dynamic plugin SDK (webpack) |
| Backend | Quarkus, serves the frontend and `/locales` over TLS on 9443 with the OpenShift service serving certificate; insecure requests disabled |
| Proxy | `/proxy/*` forwards GET, POST, PUT and DELETE with the caller's `Authorization` header to `registry.api.url` (`REGISTRY_API_URL`); 10 MB request and 50 MB response limits, 30 s timeout |
| `ConsolePlugin` | Backend service on 9443; proxy alias `registry-api` with `authorization: UserToken` |
| Naming | Namespace in the name because `ConsolePlugin` is cluster-scoped; a pre-upgrade `<name>-console-plugin` owned by the same CR is removed |
| Image | `quay.io/apicurio/apicurio-registry-console-plugin`, released by `release-images.yaml` |

## Configuration

Server side (read by `uiConfig`; full list in
[`UserInterfaceConfigProperties`](../app/src/main/java/io/apicurio/registry/ui/UserInterfaceConfigProperties.java)):

| Property | Default | Meaning |
|---|---|---|
| `apicurio.ui.contextPath`, `apicurio.ui.navPrefixPath` | `/` | Console paths |
| `apicurio.ui.docsUrl`, `apicurio.ui.editorsUrl` | `/docs/`, `/editors/` | Bundled docs and editors |
| `apicurio.ui.auth.oidc.client-id` | `apicurio-registry-ui` | Browser OIDC client |
| `apicurio.ui.auth.oidc.scope` | `openid profile email` | Requested scopes |
| `apicurio.ui.auth.oidc.redirect-uri`, `.logout-url`, `.load-user-info`, `.use-nonce` | unset | Sent only when set |
| `apicurio.ui.features.read-only.enabled` | `false` | Read-only console |
| `apicurio.ui.features.breadcrumbs`, `.settings` | `true` | UI elements |
| `apicurio.ui.features.agents.enabled` | `false` | Agents pages (also needs experimental features) |
| `apicurio.ui.features.url-import.max-content-length` | `5242880` | Import-from-URL limit |

Console image (`create-config.cjs`): `REGISTRY_API_URL` (default
`http://localhost:8080/apis/registry/v3`), `REGISTRY_CONTEXT_PATH`, `REGISTRY_NAV_PREFIX_PATH`,
`REGISTRY_DOCS_URL`, `REGISTRY_EDITORS_URL`, `REGISTRY_AUTH_*` (type, URL, client ID, scopes,
redirect and logout URLs, RBAC/OBAC, user info, nonce) and `REGISTRY_FEATURE_*` (read-only,
breadcrumbs, role management, settings, agents). A value set here wins over the server's. The
operator sets the console's `REGISTRY_API_URL` from the app ingress host
(`UIDeploymentResource`); `spec.ui.env` adds the rest.

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| Build | [`ui/package.json`](../ui/package.json), [`ui-app/package.json`](../ui/ui-app/package.json), [`package.cjs`](../ui/.scripts/package.cjs) | SDK, app, docs and editors builds; packaging |
| Image | [`Dockerfile`](../ui/Dockerfile), [`.docker-scripts/`](../ui/.docker-scripts/) | nginx, `config.js`, base href |
| Config | [`useConfigService.ts`](../ui/ui-app/src/services/useConfigService.ts), [`main.tsx`](../ui/ui-app/src/main.tsx) | Local config, `uiConfig` merge, flag defaults |
| Server | [`SystemResourceImpl`](../app/src/main/java/io/apicurio/registry/rest/v3/impl/SystemResourceImpl.java) | `GET /system/uiConfig` |
| Auth | [`MainPageWithAuth.tsx`](../ui/ui-app/src/app/MainPageWithAuth.tsx), [`rest.utils.ts`](../ui/ui-app/src/utils/rest.utils.ts), [`useReauthenticationService.ts`](../ui/ui-app/src/services/useReauthenticationService.ts) | Login, request auth, refresh on 401 |
| Plugin | [`console-extensions.json`](../console-plugin/console-extensions.json), [`EmbeddedRegistryUI.tsx`](../console-plugin/src/components/EmbeddedRegistryUI.tsx), [`RegistryProxyResource`](../console-plugin/backend/src/main/java/io/apicurio/registry/consoleplugin/RegistryProxyResource.java) | Extensions, embedded console, proxy |
| Operator | [`ConsolePluginManager`](../operator/controller/src/main/java/io/apicurio/registry/operator/feat/ConsolePluginManager.java), [`ConsolePluginDeploymentResource`](../operator/controller/src/main/java/io/apicurio/registry/operator/resource/consoleplugin/ConsolePluginDeploymentResource.java), [`UIDeploymentResource`](../operator/controller/src/main/java/io/apicurio/registry/operator/resource/ui/UIDeploymentResource.java) | `ConsolePlugin`, plugin and console Deployments |
| CI | [`verify.yaml`](../.github/workflows/verify.yaml), [`verify-extras.yaml`](../.github/workflows/verify-extras.yaml), [`verify-console-plugin.yaml`](../.github/workflows/verify-console-plugin.yaml) | Build, unit and e2e tests, plugin build |

## Limitations and open work

1. Flags hide actions by global role and server setting only; per-resource permissions are not
   reflected yet ([#10910](https://github.com/Apicurio/apicurio-registry/issues/10910),
   [#7867](https://github.com/Apicurio/apicurio-registry/issues/7867),
   [#4238](https://github.com/Apicurio/apicurio-registry/issues/4238)). Hiding is never the
   enforcement; the API is ([ADR-0017](0017-operational-guardrails.md)).
2. Local values override the server's, including feature flags, so an image environment variable can
   show an action that the server will refuse.
3. The operator's ClusterRole lacks a `console.openshift.io` rule, so `ConsolePlugin` reconciliation
   can fail with only a warning in the log
   ([#10172](https://github.com/Apicurio/apicurio-registry/issues/10172)). The operator does not add
   the plugin to the cluster's console configuration.
4. The plugin embeds the console only when the CR has no authentication; with authentication it
   links out.
5. Read-only banner text is fixed ([#10672](https://github.com/Apicurio/apicurio-registry/issues/10672));
   local setup instructions need work ([#5298](https://github.com/Apicurio/apicurio-registry/issues/5298)).
6. The console plugin has no automated UI tests; its CI job only builds it.
7. Some image settings have no effect: the `REGISTRY_DELETE_*_ENABLED` variables and the plugin's
   `masthead=false` parameter are ignored, and the plugin backend's registry URL has no scheme
   ([#11358](https://github.com/Apicurio/apicurio-registry/issues/11358)).

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| Serve the console from the registry server | Not selected: one URL and no CORS, but couples console releases, scaling and context paths to the API image |
| Configuration baked in at build time | Not selected: one image per environment; `config.js` at start keeps one image for every deployment |
| Console-only configuration (no `uiConfig`) | Not selected: every server setting would need a matching console variable that can drift; local overrides remain for split deployments |
| A hand-written API client in the console | Not selected: the generated SDK follows the spec and is built from the same commit |
| Reimplement registry pages in the OpenShift plugin | Not selected: the plugin lists instances and embeds the console, avoiding a second UI to maintain |

## Consequences

- **Benefits:** one console image for every environment; the console follows the server's
  deletion, read-only, RBAC and experimental settings without its own copy; API calls are typed
  from the same spec; OpenShift users find registry instances in their console.
- **Costs:** a second deployment and URL, with OIDC redirect setup; two frontend stacks
  (React 19/PatternFly 6 in the console, React 17/PatternFly 5 in the plugin); the console build
  depends on building the TypeScript SDK first.

## Example and verification

- **Examples:** [`examples/authentication`](../examples/authentication/) runs
  `apicurio-registry-ui` with `REGISTRY_AUTH_TYPE=oidc` against Keycloak on port 8888;
  [`examples/operator`](../examples/operator/) checks that the operator creates the console's
  Deployment, Service and NetworkPolicy. Neither checks console behaviour in a browser; their
  `demo.sh` scripts exercise the API.
- **CI:** `build-ui` in `verify.yaml` lints, runs the Vitest unit tests, builds and packages the
  console and builds its image. `ui-e2e-tests` in `verify-extras.yaml` runs the Playwright suite
  ([`ui/tests/specs`](../ui/tests/specs/), Chromium) against the registry and console images of the
  same commit, with no authentication. Both jobs feed the Verification Gate. `verify-console-plugin.yaml`
  builds the plugin frontend and backend without running tests.
- **User documentation:**
  [`assembly-managing-registry-artifacts-ui.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-managing-registry-artifacts-ui.adoc),
  the console plugin section of
  [`assembly-deploying-registry-operator.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-deploying-registry-operator.adoc).

| Concern | Tests to read |
|---|---|
| Config merge and flags | `useConfigService.test.ts`, `SystemResourceTest`, `SystemResourceLogoutUrlSetTest`, `McpImportFeatureGateTest` |
| Request auth and refresh | `rest.utils.test.ts`, `useReauthenticationService.test.ts` |
| Console in a browser | `smoke.spec.ts`, `settings.spec.ts`, `masthead.spec.ts`, `explore.spec.ts`, `globalRules.spec.ts` |
| Plugin backend | `RegistryProxyResourceTest`, `LocalesResourceTest` |
| Operator | `ConsolePluginManagerTest`, `ConsolePluginManagerOwnershipTest`, `SmokeITTest` |

```bash
(cd ui && npm install && npm run test)
(cd ui/tests && npm install && npm run test)   # needs the API on 8080 and the console on 8888
./mvnw test -pl operator/controller -Dtest='ConsolePluginManager*Test'
```
