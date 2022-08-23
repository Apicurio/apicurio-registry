| name | category | type | default | available-from | description |
| --- | --- | --- | --- | --- | --- |
| `registry.api.errors.include-stack-in-response` | `api` | `boolean` | `${REGISTRY_API_ERRORS_INCLUDE_STACKTRACE:false}` | `2.2.6-SNAPSHOT` | Include stack trace in errors responses |
| `registry.disable.apis` | `api` | `optional<list<string>>` | `/apis/ibmcompat/.*` | `2.2.6-SNAPSHOT` | Disable apis |
| `quarkus.oidc.client-id` | `auth` | `string` | `${KEYCLOAK_API_CLIENT_ID:registry-api}` | `2.2.6-SNAPSHOT` | OIDC client id |
| `registry.auth.admin-override.claim` | `auth` | `string` | `${REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM:org-admin}` | `2.2.6-SNAPSHOT` | Auth admin override claim |
| `registry.auth.admin-override.claim-value` | `auth` | `string` | `${REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM_VALUE:true}` | `2.2.6-SNAPSHOT` | Auth admin override claim value |
| `registry.auth.admin-override.enabled` | `auth` | `boolean` | `${REGISTRY_AUTH_ADMIN_OVERRIDE_ENABLED:false}` | `2.2.6-SNAPSHOT` | Auth admin override enabled |
| `registry.auth.admin-override.from` | `auth` | `string` | `${REGISTRY_AUTH_ADMIN_OVERRIDE_FROM:token}` | `2.2.6-SNAPSHOT` | Auth admin override from |
| `registry.auth.admin-override.role` | `auth` | `string` | `${REGISTRY_AUTH_ADMIN_OVERRIDE_ROLE:sr-admin}` | `2.2.6-SNAPSHOT` | Auth admin override role |
| `registry.auth.admin-override.type` | `auth` | `string` | `${REGISTRY_AUTH_ADMIN_OVERRIDE_TYPE:role}` | `2.2.6-SNAPSHOT` | Auth admin override type |
| `registry.auth.anonymous-read-access.enabled` | `auth` | `boolean [dynamic]` | `${REGISTRY_AUTH_ANONYMOUS_READS_ENABLED:${registry-legacy.auth.anonymous-read-access.enabled}}` | `2.2.6-SNAPSHOT` | Anonymous read access |
| `registry.auth.authenticated-read-access.enabled` | `auth` | `boolean [dynamic]` | `${REGISTRY_AUTH_AUTHENTICATED_READS_ENABLED:false}` | `2.2.6-SNAPSHOT` | Authenticated read access |
| `registry.auth.basic-auth-client-credentials.enabled` | `auth` | `boolean [dynamic]` | `${CLIENT_CREDENTIALS_BASIC_AUTH_ENABLED:false}` | `2.2.6-SNAPSHOT` | Enable basic auth client credentials |
| `registry.auth.client-secret` | `auth` | `optional<string>` | `${KEYCLOAK_API_CLIENT_SECRET:}` | `2.2.6-SNAPSHOT` | Auth client secret |
| `registry.auth.enabled` | `auth` | `boolean` | `${REGISTRY_AUTH_ENABLED:${registry-legacy.auth.enabled}}` | `2.2.6-SNAPSHOT` | Enable auth |
| `registry.auth.owner-only-authorization` | `auth` | `boolean [dynamic]` | `${REGISTRY_AUTH_OBAC_ENABLED:${registry-legacy.auth.owner-only-authorization}}` | `2.2.6-SNAPSHOT` | Artifact owner-only authorization |
| `registry.auth.owner-only-authorization.limit-group-access` | `auth` | `boolean [dynamic]` | `${REGISTRY_AUTH_OBAC_LIMIT_GROUP_ACCESS:false}` | `2.2.6-SNAPSHOT` | Artifact group owner-only authorization |
| `registry.auth.role-based-authorization` | `auth` | `boolean` | `${REGISTRY_AUTH_RBAC_ENABLED:${registry-legacy.auth.role-based-authorization}}` | `2.2.6-SNAPSHOT` | Enable role based authorization |
| `registry.auth.role-source` | `auth` | `string` | `${REGISTRY_AUTH_ROLE_SOURCE:${registry-legacy.auth.role-source}}` | `2.2.6-SNAPSHOT` | Auth roles source |
| `registry.auth.roles.admin` | `auth` | `string` | `${REGISTRY_AUTH_ROLES_ADMIN:sr-admin}` | `2.2.6-SNAPSHOT` | Auth roles admin |
| `registry.auth.roles.developer` | `auth` | `string` | `${REGISTRY_AUTH_ROLES_DEVELOPER:sr-developer}` | `2.2.6-SNAPSHOT` | Auth roles developer |
| `registry.auth.roles.readonly` | `auth` | `string` | `${REGISTRY_AUTH_ROLES_READONLY:sr-readonly}` | `2.2.6-SNAPSHOT` | Auth roles readonly |
| `registry.auth.tenant-owner-is-admin.enabled` | `auth` | `boolean` | `${REGISTRY_AUTH_TENANT_OWNER_IS_ADMIN:true}` | `2.2.6-SNAPSHOT` | Auth tenant owner admin enabled |
| `registry.auth.token.endpoint` | `auth` | `string` | `${TOKEN_ENDPOINT:${registry.keycloak.url}/realms/${registry.keycloak.realm}/protocol/openid-connect/token}` | `2.2.6-SNAPSHOT` | Auth token endpoint |
| `registry.config.cache.enabled` | `cache` | `boolean` | `false` | `2.2.6-SNAPSHOT` | Registry cache enabled |
| `registry.ccompat.legacy-id-mode.enabled` | `ccompat` | `boolean [dynamic]` | `${ENABLE_CCOMPAT_LEGACY_ID_MODE:false}` | `2.2.6-SNAPSHOT` | Legacy ID mode (compatibility API) |
| `registry.download.href.ttl` | `download` | `long [dynamic]` | `30` | `2.2.6-SNAPSHOT` | Download link expiry |
| `registry.events.ksink` | `events` | `optional<string>` | `` | `2.2.6-SNAPSHOT` | Events ksink enabled |
| `registry.liveness.errors.ignored` | `health` | `optional<list<string>>` | `` | `2.2.6-SNAPSHOT` | Ignored liveness errors |
| `registry.metrics.PersistenceExceptionLivenessCheck.counterResetWindowDurationSec` | `health` | `integer` | `60` | `2.2.6-SNAPSHOT` | Counter reset window duration of persistence liveness check |
| `registry.metrics.PersistenceExceptionLivenessCheck.disableLogging` | `health` | `boolean` | `false` | `2.2.6-SNAPSHOT` | Disable logging of persistence liveness check |
| `registry.metrics.PersistenceExceptionLivenessCheck.errorThreshold` | `health` | `integer` | `1` | `2.2.6-SNAPSHOT` | Error Threshold of persistence liveness check |
| `registry.metrics.PersistenceExceptionLivenessCheck.statusResetWindowDurationSec` | `health` | `integer` | `300` | `2.2.6-SNAPSHOT` | Status reset window duration of persistence liveness check |
| `registry.metrics.PersistenceTimeoutReadinessCheck.counterResetWindowDurationSec` | `health` | `integer` | `60` | `2.2.6-SNAPSHOT` | Counter reset window duration of persistence readiness check |
| `registry.metrics.PersistenceTimeoutReadinessCheck.errorThreshold` | `health` | `integer` | `5` | `2.2.6-SNAPSHOT` | Error Threshold of persistence readiness check |
| `registry.metrics.PersistenceTimeoutReadinessCheck.statusResetWindowDurationSec` | `health` | `integer` | `300` | `2.2.6-SNAPSHOT` | Status reset window duration of persistence readiness check |
| `registry.metrics.PersistenceTimeoutReadinessCheck.timeoutSec` | `health` | `integer` | `15` | `2.2.6-SNAPSHOT` | Timeout of persistence readiness check |
| `registry.metrics.ResponseErrorLivenessCheck.counterResetWindowDurationSec` | `health` | `integer` | `60` | `2.2.6-SNAPSHOT` | Counter reset window duration of response liveness check |
| `registry.metrics.ResponseErrorLivenessCheck.disableLogging` | `health` | `boolean` | `false` | `2.2.6-SNAPSHOT` | Disable logging of response liveness check |
| `registry.metrics.ResponseErrorLivenessCheck.errorThreshold` | `health` | `integer` | `1` | `2.2.6-SNAPSHOT` | Error Threshold of response liveness check |
| `registry.metrics.ResponseErrorLivenessCheck.statusResetWindowDurationSec` | `health` | `integer` | `300` | `2.2.6-SNAPSHOT` | Status reset window duration of response liveness check |
| `registry.metrics.ResponseTimeoutReadinessCheck.counterResetWindowDurationSec` | `health` | `integer` | `60` | `2.2.6-SNAPSHOT` | Counter reset window duration of response readiness check |
| `registry.metrics.ResponseTimeoutReadinessCheck.errorThreshold` | `health` | `integer` | `1` | `2.2.6-SNAPSHOT` | Error Threshold of response readiness check |
| `registry.metrics.ResponseTimeoutReadinessCheck.statusResetWindowDurationSec` | `health` | `integer` | `300` | `2.2.6-SNAPSHOT` | Status reset window duration of response readiness check |
| `registry.metrics.ResponseTimeoutReadinessCheck.timeoutSec` | `health` | `integer` | `10` | `2.2.6-SNAPSHOT` | Timeout of response readiness check |
| `registry.storage.metrics.cache.check-period` | `health` | `long` | `30000` | `2.2.6-SNAPSHOT` | Storage metrics cache check period |
| `registry.import.url` | `import` | `optional<url>` | `` | `2.2.6-SNAPSHOT` | The import URL |
| `registry.events.kafka.topic` | `kafka` | `optional<string>` | `` | `2.2.6-SNAPSHOT` | Events Kafka topic |
| `registry.events.kafka.topic-partition` | `kafka` | `optional<integer>` | `` | `2.2.6-SNAPSHOT` | Events Kafka topic partition |
| `registry.limits.config.cache.check-period` | `limits` | `long` | `30000` | `2.2.6-SNAPSHOT` | Cache check period limit |
| `registry.limits.config.max-artifact-labels` | `limits` | `long` | `-1` | `2.2.6-SNAPSHOT` | Max artifact labels |
| `registry.limits.config.max-artifact-properties` | `limits` | `long` | `-1` | `2.2.6-SNAPSHOT` | Max artifact properties |
| `registry.limits.config.max-artifacts` | `limits` | `long` | `-1` | `2.2.6-SNAPSHOT` | Max artifacts |
| `registry.limits.config.max-description-length` | `limits` | `long` | `-1` | `2.2.6-SNAPSHOT` | Max artifact max description length |
| `registry.limits.config.max-label-size` | `limits` | `long` | `-1` | `2.2.6-SNAPSHOT` | Max artifact max label size |
| `registry.limits.config.max-name-length` | `limits` | `long` | `-1` | `2.2.6-SNAPSHOT` | Max artifact max name length |
| `registry.limits.config.max-property-key-size` | `limits` | `long` | `-1` | `2.2.6-SNAPSHOT` | Max artifact property key size |
| `registry.limits.config.max-property-value-size` | `limits` | `long` | `-1` | `2.2.6-SNAPSHOT` | Max artifact property value size |
| `registry.limits.config.max-requests-per-second` | `limits` | `long` | `-1` | `2.2.6-SNAPSHOT` | Max artifact max requests per second |
| `registry.limits.config.max-schema-size-bytes` | `limits` | `long` | `-1` | `2.2.6-SNAPSHOT` | Max schema size (bytes) |
| `registry.limits.config.max-total-schemas` | `limits` | `long` | `-1` | `2.2.6-SNAPSHOT` | Max total schemas |
| `registry.limits.config.max-versions-per-artifact` | `limits` | `long` | `-1` | `2.2.6-SNAPSHOT` | Max versions per artifacts |
| `quarkus.log.level` | `log` | `string` | `` | `2.2.6-SNAPSHOT` | Log level |
| `registry.enable.multitenancy` | `mt` | `boolean` | `false` | `2.2.6-SNAPSHOT` | Enable multitenancy |
| `registry.multitenancy.authorization.enabled` | `mt` | `boolean` | `true` | `2.2.6-SNAPSHOT` | Enable multitenancy authorization |
| `registry.multitenancy.reaper.every` | `mt` | `optional<string>` | `` | `2.2.6-SNAPSHOT` | Multitenancy reaper every |
| `registry.multitenancy.reaper.max-tenants-reaped` | `mt` | `int` | `100` | `2.2.6-SNAPSHOT` | Multitenancy reaper max tenants reaped |
| `registry.multitenancy.reaper.period-seconds` | `mt` | `long` | `10800` | `2.2.6-SNAPSHOT` | Multitenancy reaper period seconds |
| `registry.multitenancy.types.context-path.base-path` | `mt` | `string` | `t` | `2.2.6-SNAPSHOT` | Multitenancy context path type base path |
| `registry.multitenancy.types.context-path.enabled` | `mt` | `boolean` | `true` | `2.2.6-SNAPSHOT` | Enable multitenancy context path type |
| `registry.multitenancy.types.request-header.enabled` | `mt` | `boolean` | `true` | `2.2.6-SNAPSHOT` | Enable multitenancy request header type |
| `registry.multitenancy.types.request-header.name` | `mt` | `string` | `X-Registry-Tenant-Id` | `2.2.6-SNAPSHOT` | Multitenancy request header type name |
| `registry.multitenancy.types.subdomain.enabled` | `mt` | `boolean` | `false` | `2.2.6-SNAPSHOT` | Enable multitenancy subdomain type |
| `registry.multitenancy.types.subdomain.header-name` | `mt` | `string` | `Host` | `2.2.6-SNAPSHOT` | Multitenancy subdomain type header name |
| `registry.multitenancy.types.subdomain.location` | `mt` | `string` | `header` | `2.2.6-SNAPSHOT` | Multitenancy subdomain type location |
| `registry.multitenancy.types.subdomain.pattern` | `mt` | `string` | `(\w[\w\d\-]*)\.localhost\.local` | `2.2.6-SNAPSHOT` | Multitenancy subdomain type pattern |
| `registry.organization-id.claim-name` | `mt` | `list<string>` | `${ORGANIZATION_ID_CLAIM:rh-org-id}` | `2.2.6-SNAPSHOT` | Organization Id claim name |
| `registry.tenant.manager.auth.client-id` | `mt` | `optional<string>` | `${TENANT_MANAGER_CLIENT_ID:registry-api}` | `2.2.6-SNAPSHOT` | Tenant manager auth client id |
| `registry.tenant.manager.auth.client-secret` | `mt` | `optional<string>` | `${TENANT_MANAGER_CLIENT_SECRET:default_secret}` | `2.2.6-SNAPSHOT` | Tenant manager auth client secret |
| `registry.tenant.manager.auth.enabled` | `mt` | `optional<boolean>` | `${TENANT_MANAGER_AUTH_ENABLED:${registry.auth.enabled}}` | `2.2.6-SNAPSHOT` | Tenant manager auth enabled |
| `registry.tenant.manager.auth.token.expiration.reduction.ms` | `mt` | `optional<long>` | `${TENANT_MANAGER_AUTH_TOKEN_EXP_REDUCTION_MS:}` | `2.2.6-SNAPSHOT` | Tenant manager auth token expiration reduction ms |
| `registry.tenant.manager.auth.url.configured` | `mt` | `optional<string>` | `${TENANT_MANAGER_TOKEN_ENDPOINT:${registry.tenant.manager.auth.url}/realms/${registry.tenant.manager.auth.realm}/protocol/openid-connect/token}` | `2.2.6-SNAPSHOT` | Tenant manager auth url configured |
| `registry.tenant.manager.ssl.ca.path` | `mt` | `optional<string>` | `` | `2.2.6-SNAPSHOT` | Tenant manager SSL ca path |
| `registry.tenant.manager.url` | `mt` | `optional<string>` | `${TENANT_MANAGER_URL:http://localhost:8585}` | `2.2.6-SNAPSHOT` | Tenant manager URL |
| `registry.tenants.context.cache.check-period` | `mt` | `long` | `60000` | `2.2.6-SNAPSHOT` | Tenants context cache check period |
| `registry.enable-redirects` | `redirects` | `boolean` | `${REGISTRY_ENABLE_REDIRECTS:true}` | `2.2.6-SNAPSHOT` | Enable redirects |
| `registry.redirects` | `redirects` | `map<string, string>` | `` | `2.2.6-SNAPSHOT` | Registry redirects |
| `quarkus.datasource.db-kind` | `store` | `string` | `postgresql` | `2.2.6-SNAPSHOT` | Datasource Db kind |
| `quarkus.datasource.jdbc.url` | `store` | `string` | `` | `2.2.6-SNAPSHOT` | Datasource jdbc URL |
| `registry.sql.init` | `store` | `boolean` | `true` | `2.2.6-SNAPSHOT` | SQL init |
| `quarkus.oidc.tenant-enabled` | `ui` | `boolean` | `${registry.auth.enabled}` | `2.2.6-SNAPSHOT` | UI OIDC tenant enabled |
| `registry.ui.config.apiUrl` | `ui` | `string` | `` | `2.2.6-SNAPSHOT` | UI api URL |
| `registry.ui.config.auth.oidc.client-id` | `ui` | `string` | `${REGISTRY_OIDC_UI_CLIENT_ID:default_client}` | `2.2.6-SNAPSHOT` | UI auth OIDC client ID |
| `registry.ui.config.auth.oidc.redirect-url` | `ui` | `string` | `${REGISTRY_OIDC_UI_REDIRECT_URL:http://localhost:8080}` | `2.2.6-SNAPSHOT` | UI auth OIDC redirect URL |
| `registry.ui.config.auth.oidc.url` | `ui` | `string` | `${REGISTRY_AUTH_URL_CONFIGURED:http://localhost:8090}` | `2.2.6-SNAPSHOT` | UI auth OIDC URL |
| `registry.ui.config.auth.type` | `ui` | `string` | `${REGISTRY_UI_AUTH_TYPE:none}` | `2.2.6-SNAPSHOT` | UI auth type |
| `registry.ui.config.uiContextPath` | `ui` | `string` | `/ui/` | `2.2.6-SNAPSHOT` | UI context path |
| `registry.ui.features.readOnly` | `ui` | `boolean [dynamic]` | `${REGISTRY_UI_FEATURES_READONLY:false}` | `2.2.6-SNAPSHOT` | UI read-only mode |
| `registry.ui.features.settings` | `ui` | `boolean` | `true` | `2.2.6-SNAPSHOT` | UI features settings |
| `registry.auth.anonymous-read-access.enabled.dynamic.allow` | `unknown` | `unknown` | `${registry.config.dynamic.allow-all}` | `` |  |
| `registry.auth.basic-auth-client-credentials.enabled.dynamic.allow` | `unknown` | `unknown` | `${registry.config.dynamic.allow-all}` | `` |  |
| `registry.auth.owner-only-authorization.dynamic.allow` | `unknown` | `unknown` | `${registry.config.dynamic.allow-all}` | `` |  |
| `registry.auth.owner-only-authorization.limit-group-access.dynamic.allow` | `unknown` | `unknown` | `${registry.config.dynamic.allow-all}` | `` |  |
| `registry.auth.url.configured` | `unknown` | `unknown` | `${registry.keycloak.url}/realms/${registry.keycloak.realm}` | `` |  |
| `registry.ccompat.legacy-id-mode.enabled.dynamic.allow` | `unknown` | `unknown` | `${registry.config.dynamic.allow-all}` | `` |  |
| `registry.config.dynamic.allow-all` | `unknown` | `unknown` | `${REGISTRY_ALLOW_DYNAMIC_CONFIG:true}` | `` |  |
| `registry.config.refresh.every` | `unknown` | `unknown` | `1m` | `` |  |
| `registry.date` | `unknown` | `unknown` | `${timestamp}` | `` |  |
| `registry.description` | `unknown` | `unknown` | `High performance, runtime registry for schemas and API designs.` | `` |  |
| `registry.download.href.ttl.dynamic.allow` | `unknown` | `unknown` | `${registry.config.dynamic.allow-all}` | `` |  |
| `registry.downloads.reaper.every` | `unknown` | `unknown` | `60s` | `` |  |
| `registry.enable.sentry` | `unknown` | `unknown` | `${ENABLE_SENTRY:false}` | `` |  |
| `registry.id` | `unknown` | `unknown` | `apicurio-registry` | `` |  |
| `registry.keycloak.realm` | `unknown` | `unknown` | `${KEYCLOAK_REALM:apicurio-local}` | `` |  |
| `registry.keycloak.url` | `unknown` | `unknown` | `${KEYCLOAK_URL:http://localhost:8090/auth}` | `` |  |
| `registry.name` | `unknown` | `unknown` | `Apicurio Registry (In Memory)` | `` |  |
| `registry.redirects.root` | `unknown` | `unknown` | `/,${REGISTRY_ROOT_REDIRECT:/ui/}` | `` |  |
| `registry.tenant.manager.auth.realm` | `unknown` | `unknown` | `${TENANT_MANAGER_REALM:registry}` | `` |  |
| `registry.tenant.manager.auth.url` | `unknown` | `unknown` | `${TENANT_MANAGER_AUTH_URL:http://localhost:8090/auth}` | `` |  |
| `registry.ui.config.auth.keycloak.clientId` | `unknown` | `unknown` | `${KEYCLOAK_UI_CLIENT_ID:apicurio-registry}` | `` |  |
| `registry.ui.config.auth.keycloak.onLoad` | `unknown` | `unknown` | `login-required` | `` |  |
| `registry.ui.config.auth.keycloak.realm` | `unknown` | `unknown` | `${registry.keycloak.realm}` | `` |  |
| `registry.ui.config.auth.keycloak.url` | `unknown` | `unknown` | `${registry.keycloak.url}` | `` |  |
| `registry.ui.features.readOnly.dynamic.allow` | `unknown` | `unknown` | `${registry.config.dynamic.allow-all}` | `` |  |
| `registry.version` | `unknown` | `unknown` | `${project.version}` | `` |  |
