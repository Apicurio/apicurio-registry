| name | type | default | description |
| --- | --- | --- | --- |
| `quarkus.datasource.db-kind` | `string` | `postgresql` |  |
| `quarkus.datasource.jdbc.url` | `string` | `` |  |
| `quarkus.log.level` | `string` | `` |  |
| `quarkus.oidc.client-id` | `string` | `${KEYCLOAK_API_CLIENT_ID:registry-api}` |  |
| `quarkus.oidc.tenant-enabled` | `boolean` | `${registry.auth.enabled}` |  |
| `registry.api.errors.include-stack-in-response` | `boolean` | `${REGISTRY_API_ERRORS_INCLUDE_STACKTRACE:false}` |  |
| `registry.auth.admin-override.claim` | `string` | `${REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM:org-admin}` |  |
| `registry.auth.admin-override.claim-value` | `string` | `${REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM_VALUE:true}` |  |
| `registry.auth.admin-override.enabled` | `boolean` | `${REGISTRY_AUTH_ADMIN_OVERRIDE_ENABLED:false}` |  |
| `registry.auth.admin-override.from` | `string` | `${REGISTRY_AUTH_ADMIN_OVERRIDE_FROM:token}` |  |
| `registry.auth.admin-override.role` | `string` | `${REGISTRY_AUTH_ADMIN_OVERRIDE_ROLE:sr-admin}` |  |
| `registry.auth.admin-override.type` | `string` | `${REGISTRY_AUTH_ADMIN_OVERRIDE_TYPE:role}` |  |
| `registry.auth.anonymous-read-access.enabled` | `boolean [dynamic]` | `${REGISTRY_AUTH_ANONYMOUS_READS_ENABLED:${registry-legacy.auth.anonymous-read-access.enabled}}` | When selected, requests from anonymous users (requests without any credentials) are granted read-only access. |
| `registry.auth.anonymous-read-access.enabled.dynamic.allow` | `unknown` | `${registry.config.dynamic.allow-all}` |  |
| `registry.auth.authenticated-read-access.enabled` | `boolean [dynamic]` | `${REGISTRY_AUTH_AUTHENTICATED_READS_ENABLED:false}` | When selected, requests from any authenticated user are granted at least read-only access. |
| `registry.auth.basic-auth-client-credentials.enabled` | `boolean [dynamic]` | `${CLIENT_CREDENTIALS_BASIC_AUTH_ENABLED:false}` | When selected, users are permitted to authenticate using HTTP basic authentication (in addition to OAuth). |
| `registry.auth.basic-auth-client-credentials.enabled.dynamic.allow` | `unknown` | `${registry.config.dynamic.allow-all}` |  |
| `registry.auth.client-secret` | `optional<string>` | `${KEYCLOAK_API_CLIENT_SECRET:}` |  |
| `registry.auth.enabled` | `boolean` | `${REGISTRY_AUTH_ENABLED:${registry-legacy.auth.enabled}}` |  |
| `registry.auth.owner-only-authorization` | `boolean [dynamic]` | `${REGISTRY_AUTH_OBAC_ENABLED:${registry-legacy.auth.owner-only-authorization}}` | When selected, Service Registry allows only the artifact owner (creator) to modify an artifact. |
| `registry.auth.owner-only-authorization.dynamic.allow` | `unknown` | `${registry.config.dynamic.allow-all}` |  |
| `registry.auth.owner-only-authorization.limit-group-access` | `boolean [dynamic]` | `${REGISTRY_AUTH_OBAC_LIMIT_GROUP_ACCESS:false}` | When selected, Service Registry allows only the artifact group owner (creator) to modify an artifact group. |
| `registry.auth.owner-only-authorization.limit-group-access.dynamic.allow` | `unknown` | `${registry.config.dynamic.allow-all}` |  |
| `registry.auth.role-based-authorization` | `boolean` | `${REGISTRY_AUTH_RBAC_ENABLED:${registry-legacy.auth.role-based-authorization}}` |  |
| `registry.auth.role-source` | `string` | `${REGISTRY_AUTH_ROLE_SOURCE:${registry-legacy.auth.role-source}}` |  |
| `registry.auth.roles.admin` | `string` | `${REGISTRY_AUTH_ROLES_ADMIN:sr-admin}` |  |
| `registry.auth.roles.developer` | `string` | `${REGISTRY_AUTH_ROLES_DEVELOPER:sr-developer}` |  |
| `registry.auth.roles.readonly` | `string` | `${REGISTRY_AUTH_ROLES_READONLY:sr-readonly}` |  |
| `registry.auth.tenant-owner-is-admin.enabled` | `boolean` | `${REGISTRY_AUTH_TENANT_OWNER_IS_ADMIN:true}` |  |
| `registry.auth.token.endpoint` | `string` | `${TOKEN_ENDPOINT:${registry.keycloak.url}/realms/${registry.keycloak.realm}/protocol/openid-connect/token}` |  |
| `registry.auth.url.configured` | `unknown` | `${registry.keycloak.url}/realms/${registry.keycloak.realm}` |  |
| `registry.ccompat.legacy-id-mode.enabled` | `boolean [dynamic]` | `${ENABLE_CCOMPAT_LEGACY_ID_MODE:false}` | When selected, the Schema Registry compatibility API uses global ID instead of content ID for artifact identifiers. |
| `registry.ccompat.legacy-id-mode.enabled.dynamic.allow` | `unknown` | `${registry.config.dynamic.allow-all}` |  |
| `registry.config.cache.enabled` | `boolean` | `false` |  |
| `registry.config.dynamic.allow-all` | `unknown` | `${REGISTRY_ALLOW_DYNAMIC_CONFIG:true}` |  |
| `registry.config.refresh.every` | `unknown` | `1m` |  |
| `registry.date` | `unknown` | `${timestamp}` |  |
| `registry.description` | `unknown` | `High performance, runtime registry for schemas and API designs.` |  |
| `registry.disable.apis` | `optional<list<string>>` | `/apis/ibmcompat/.*` |  |
| `registry.download.href.ttl` | `long [dynamic]` | `30` | The number of seconds that a generated link to a .zip download file is active before expiring. |
| `registry.download.href.ttl.dynamic.allow` | `unknown` | `${registry.config.dynamic.allow-all}` |  |
| `registry.downloads.reaper.every` | `unknown` | `60s` |  |
| `registry.enable-redirects` | `boolean` | `${REGISTRY_ENABLE_REDIRECTS:true}` |  |
| `registry.enable.multitenancy` | `boolean` | `false` |  |
| `registry.enable.sentry` | `unknown` | `${ENABLE_SENTRY:false}` |  |
| `registry.events.kafka.topic` | `optional<string>` | `` |  |
| `registry.events.kafka.topic-partition` | `optional<integer>` | `` |  |
| `registry.events.ksink` | `optional<string>` | `` |  |
| `registry.id` | `unknown` | `apicurio-registry` |  |
| `registry.import.url` | `optional<url>` | `` |  |
| `registry.keycloak.realm` | `unknown` | `${KEYCLOAK_REALM:apicurio-local}` |  |
| `registry.keycloak.url` | `unknown` | `${KEYCLOAK_URL:http://localhost:8090/auth}` |  |
| `registry.limits.config.cache.check-period` | `long` | `30000` |  |
| `registry.limits.config.max-artifact-labels` | `long` | `-1` |  |
| `registry.limits.config.max-artifact-properties` | `long` | `-1` |  |
| `registry.limits.config.max-artifacts` | `long` | `-1` |  |
| `registry.limits.config.max-description-length` | `long` | `-1` |  |
| `registry.limits.config.max-label-size` | `long` | `-1` |  |
| `registry.limits.config.max-name-length` | `long` | `-1` |  |
| `registry.limits.config.max-property-key-size` | `long` | `-1` |  |
| `registry.limits.config.max-property-value-size` | `long` | `-1` |  |
| `registry.limits.config.max-requests-per-second` | `long` | `-1` |  |
| `registry.limits.config.max-schema-size-bytes` | `long` | `-1` |  |
| `registry.limits.config.max-total-schemas` | `long` | `-1` |  |
| `registry.limits.config.max-versions-per-artifact` | `long` | `-1` |  |
| `registry.liveness.errors.ignored` | `optional<list<string>>` | `` |  |
| `registry.metrics.PersistenceExceptionLivenessCheck.counterResetWindowDurationSec` | `integer` | `60` |  |
| `registry.metrics.PersistenceExceptionLivenessCheck.disableLogging` | `boolean` | `false` |  |
| `registry.metrics.PersistenceExceptionLivenessCheck.errorThreshold` | `integer` | `1` |  |
| `registry.metrics.PersistenceExceptionLivenessCheck.statusResetWindowDurationSec` | `integer` | `300` |  |
| `registry.metrics.PersistenceTimeoutReadinessCheck.counterResetWindowDurationSec` | `integer` | `60` |  |
| `registry.metrics.PersistenceTimeoutReadinessCheck.errorThreshold` | `integer` | `5` |  |
| `registry.metrics.PersistenceTimeoutReadinessCheck.statusResetWindowDurationSec` | `integer` | `300` |  |
| `registry.metrics.PersistenceTimeoutReadinessCheck.timeoutSec` | `integer` | `15` |  |
| `registry.metrics.ResponseErrorLivenessCheck.counterResetWindowDurationSec` | `integer` | `60` |  |
| `registry.metrics.ResponseErrorLivenessCheck.disableLogging` | `boolean` | `false` |  |
| `registry.metrics.ResponseErrorLivenessCheck.errorThreshold` | `integer` | `1` |  |
| `registry.metrics.ResponseErrorLivenessCheck.statusResetWindowDurationSec` | `integer` | `300` |  |
| `registry.metrics.ResponseTimeoutReadinessCheck.counterResetWindowDurationSec` | `integer` | `60` |  |
| `registry.metrics.ResponseTimeoutReadinessCheck.errorThreshold` | `integer` | `1` |  |
| `registry.metrics.ResponseTimeoutReadinessCheck.statusResetWindowDurationSec` | `integer` | `300` |  |
| `registry.metrics.ResponseTimeoutReadinessCheck.timeoutSec` | `integer` | `10` |  |
| `registry.multitenancy.authorization.enabled` | `boolean` | `true` |  |
| `registry.multitenancy.reaper.every` | `optional<string>` | `` |  |
| `registry.multitenancy.reaper.max-tenants-reaped` | `int` | `100` |  |
| `registry.multitenancy.reaper.period-seconds` | `long` | `10800` |  |
| `registry.multitenancy.types.context-path.base-path` | `string` | `t` |  |
| `registry.multitenancy.types.context-path.enabled` | `boolean` | `true` |  |
| `registry.multitenancy.types.request-header.enabled` | `boolean` | `true` |  |
| `registry.multitenancy.types.request-header.name` | `string` | `X-Registry-Tenant-Id` |  |
| `registry.multitenancy.types.subdomain.enabled` | `boolean` | `false` |  |
| `registry.multitenancy.types.subdomain.header-name` | `string` | `Host` |  |
| `registry.multitenancy.types.subdomain.location` | `string` | `header` |  |
| `registry.multitenancy.types.subdomain.pattern` | `string` | `(\w[\w\d\-]*)\.localhost\.local` |  |
| `registry.name` | `unknown` | `Apicurio Registry (In Memory)` |  |
| `registry.organization-id.claim-name` | `list<string>` | `${ORGANIZATION_ID_CLAIM:rh-org-id}` |  |
| `registry.redirects` | `map<string, string>` | `` |  |
| `registry.redirects.root` | `unknown` | `/,${REGISTRY_ROOT_REDIRECT:/ui/}` |  |
| `registry.sql.init` | `boolean` | `true` |  |
| `registry.storage.metrics.cache.check-period` | `long` | `30000` |  |
| `registry.tenant.manager.auth.client-id` | `optional<string>` | `${TENANT_MANAGER_CLIENT_ID:registry-api}` |  |
| `registry.tenant.manager.auth.client-secret` | `optional<string>` | `${TENANT_MANAGER_CLIENT_SECRET:default_secret}` |  |
| `registry.tenant.manager.auth.enabled` | `optional<boolean>` | `${TENANT_MANAGER_AUTH_ENABLED:true}` |  |
| `registry.tenant.manager.auth.realm` | `unknown` | `${TENANT_MANAGER_REALM:registry}` |  |
| `registry.tenant.manager.auth.token.expiration.reduction.ms` | `optional<long>` | `${TENANT_MANAGER_AUTH_TOKEN_EXP_REDUCTION_MS:}` |  |
| `registry.tenant.manager.auth.url` | `unknown` | `${TENANT_MANAGER_AUTH_URL:http://localhost:8090/auth}` |  |
| `registry.tenant.manager.auth.url.configured` | `optional<string>` | `${TENANT_MANAGER_TOKEN_ENDPOINT:${registry.tenant.manager.auth.url}/realms/${registry.tenant.manager.auth.realm}/protocol/openid-connect/token}` |  |
| `registry.tenant.manager.ssl.ca.path` | `optional<string>` | `` |  |
| `registry.tenant.manager.url` | `optional<string>` | `${TENANT_MANAGER_URL:http://localhost:8585}` |  |
| `registry.tenants.context.cache.check-period` | `long` | `60000` |  |
| `registry.ui.config.apiUrl` | `string` | `` |  |
| `registry.ui.config.auth.keycloak.clientId` | `unknown` | `${KEYCLOAK_UI_CLIENT_ID:apicurio-registry}` |  |
| `registry.ui.config.auth.keycloak.onLoad` | `unknown` | `login-required` |  |
| `registry.ui.config.auth.keycloak.realm` | `unknown` | `${registry.keycloak.realm}` |  |
| `registry.ui.config.auth.keycloak.url` | `unknown` | `${registry.keycloak.url}` |  |
| `registry.ui.config.uiContextPath` | `string` | `/ui/` |  |
| `registry.ui.features.readOnly` | `boolean [dynamic]` | `${REGISTRY_UI_FEATURES_READONLY:false}` | When selected, the Service Registry web console is set to read-only, preventing create, read, update, or delete operations. |
| `registry.ui.features.readOnly.dynamic.allow` | `unknown` | `${registry.config.dynamic.allow-all}` |  |
| `registry.ui.features.settings` | `boolean` | `true` |  |
| `registry.version` | `unknown` | `${project.version}` |  |
