| name | type | default | available-from | description |
| --- | --- | --- | --- | --- |
| `quarkus.datasource.db-kind` | `string` | `postgresql` | `2.0.0.Final` |  |
| `quarkus.datasource.jdbc.url` | `string` | `` | `2.1.0.Final` |  |
| `quarkus.log.level` | `string` | `` | `2.0.0.Final` |  |
| `quarkus.oidc.client-id` | `string` | `${KEYCLOAK_API_CLIENT_ID:registry-api}` | `2.0.0.Final` |  |
| `quarkus.oidc.tenant-enabled` | `boolean` | `${registry.auth.enabled}` | `2.0.0.Final` |  |
| `registry.api.errors.include-stack-in-response` | `boolean` | `${REGISTRY_API_ERRORS_INCLUDE_STACKTRACE:false}` | `2.1.4.Final` |  |
| `registry.auth.admin-override.claim` | `string` | `${REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM:org-admin}` | `2.1.0.Final` |  |
| `registry.auth.admin-override.claim-value` | `string` | `${REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM_VALUE:true}` | `2.1.0.Final` |  |
| `registry.auth.admin-override.enabled` | `boolean` | `${REGISTRY_AUTH_ADMIN_OVERRIDE_ENABLED:false}` | `2.1.0.Final` |  |
| `registry.auth.admin-override.from` | `string` | `${REGISTRY_AUTH_ADMIN_OVERRIDE_FROM:token}` | `2.1.0.Final` |  |
| `registry.auth.admin-override.role` | `string` | `${REGISTRY_AUTH_ADMIN_OVERRIDE_ROLE:sr-admin}` | `2.1.0.Final` |  |
| `registry.auth.admin-override.type` | `string` | `${REGISTRY_AUTH_ADMIN_OVERRIDE_TYPE:role}` | `2.1.0.Final` |  |
| `registry.auth.anonymous-read-access.enabled` | `boolean [dynamic]` | `${REGISTRY_AUTH_ANONYMOUS_READS_ENABLED:${registry-legacy.auth.anonymous-read-access.enabled}}` | `2.1.0.Final` | When selected, requests from anonymous users (requests without any credentials) are granted read-only access. |
| `registry.auth.anonymous-read-access.enabled.dynamic.allow` | `unknown` | `${registry.config.dynamic.allow-all}` | `2.2.6-SNAPSHOT` |  |
| `registry.auth.authenticated-read-access.enabled` | `boolean [dynamic]` | `${REGISTRY_AUTH_AUTHENTICATED_READS_ENABLED:false}` | `2.1.4.Final` | When selected, requests from any authenticated user are granted at least read-only access. |
| `registry.auth.basic-auth-client-credentials.enabled` | `boolean [dynamic]` | `${CLIENT_CREDENTIALS_BASIC_AUTH_ENABLED:false}` | `2.1.0.Final` | When selected, users are permitted to authenticate using HTTP basic authentication (in addition to OAuth). |
| `registry.auth.basic-auth-client-credentials.enabled.dynamic.allow` | `unknown` | `${registry.config.dynamic.allow-all}` | `2.2.6-SNAPSHOT` |  |
| `registry.auth.client-secret` | `optional<string>` | `${KEYCLOAK_API_CLIENT_SECRET:}` | `2.1.0.Final` |  |
| `registry.auth.enabled` | `boolean` | `${REGISTRY_AUTH_ENABLED:${registry-legacy.auth.enabled}}` | `2.0.0.Final` |  |
| `registry.auth.owner-only-authorization` | `boolean [dynamic]` | `${REGISTRY_AUTH_OBAC_ENABLED:${registry-legacy.auth.owner-only-authorization}}` | `2.0.0.Final` | When selected, Service Registry allows only the artifact owner (creator) to modify an artifact. |
| `registry.auth.owner-only-authorization.dynamic.allow` | `unknown` | `${registry.config.dynamic.allow-all}` | `2.2.6-SNAPSHOT` |  |
| `registry.auth.owner-only-authorization.limit-group-access` | `boolean [dynamic]` | `${REGISTRY_AUTH_OBAC_LIMIT_GROUP_ACCESS:false}` | `2.1.0.Final` | When selected, Service Registry allows only the artifact group owner (creator) to modify an artifact group. |
| `registry.auth.owner-only-authorization.limit-group-access.dynamic.allow` | `unknown` | `${registry.config.dynamic.allow-all}` | `2.2.6-SNAPSHOT` |  |
| `registry.auth.role-based-authorization` | `boolean` | `${REGISTRY_AUTH_RBAC_ENABLED:${registry-legacy.auth.role-based-authorization}}` | `2.1.0.Final` |  |
| `registry.auth.role-source` | `string` | `${REGISTRY_AUTH_ROLE_SOURCE:${registry-legacy.auth.role-source}}` | `2.1.0.Final` |  |
| `registry.auth.roles.admin` | `string` | `${REGISTRY_AUTH_ROLES_ADMIN:sr-admin}` | `2.0.0.Final` |  |
| `registry.auth.roles.developer` | `string` | `${REGISTRY_AUTH_ROLES_DEVELOPER:sr-developer}` | `2.1.0.Final` |  |
| `registry.auth.roles.readonly` | `string` | `${REGISTRY_AUTH_ROLES_READONLY:sr-readonly}` | `2.1.0.Final` |  |
| `registry.auth.tenant-owner-is-admin.enabled` | `boolean` | `${REGISTRY_AUTH_TENANT_OWNER_IS_ADMIN:true}` | `2.1.0.Final` |  |
| `registry.auth.token.endpoint` | `string` | `${TOKEN_ENDPOINT:${registry.keycloak.url}/realms/${registry.keycloak.realm}/protocol/openid-connect/token}` | `2.1.0.Final` |  |
| `registry.auth.url.configured` | `unknown` | `${registry.keycloak.url}/realms/${registry.keycloak.realm}` | `2.2.6-SNAPSHOT` |  |
| `registry.ccompat.legacy-id-mode.enabled` | `boolean [dynamic]` | `${ENABLE_CCOMPAT_LEGACY_ID_MODE:false}` | `2.0.2.Final` | When selected, the Schema Registry compatibility API uses global ID instead of content ID for artifact identifiers. |
| `registry.ccompat.legacy-id-mode.enabled.dynamic.allow` | `unknown` | `${registry.config.dynamic.allow-all}` | `2.2.6-SNAPSHOT` |  |
| `registry.config.cache.enabled` | `boolean` | `false` | `2.2.2.Final` |  |
| `registry.config.dynamic.allow-all` | `unknown` | `${REGISTRY_ALLOW_DYNAMIC_CONFIG:true}` | `2.2.6-SNAPSHOT` |  |
| `registry.config.refresh.every` | `unknown` | `1m` | `2.2.6-SNAPSHOT` |  |
| `registry.date` | `unknown` | `${timestamp}` | `2.0.0.Final` |  |
| `registry.description` | `unknown` | `High performance, runtime registry for schemas and API designs.` | `2.0.0.Final` |  |
| `registry.disable.apis` | `optional<list<string>>` | `/apis/ibmcompat/.*` | `2.0.0.Final` |  |
| `registry.download.href.ttl` | `long [dynamic]` | `30` | `2.1.2.Final` | The number of seconds that a generated link to a .zip download file is active before expiring. |
| `registry.download.href.ttl.dynamic.allow` | `unknown` | `${registry.config.dynamic.allow-all}` | `2.2.6-SNAPSHOT` |  |
| `registry.downloads.reaper.every` | `unknown` | `60s` | `2.2.6-SNAPSHOT` |  |
| `registry.enable-redirects` | `boolean` | `${REGISTRY_ENABLE_REDIRECTS:true}` | `2.1.2.Final` |  |
| `registry.enable.multitenancy` | `boolean` | `false` | `2.0.0.Final` |  |
| `registry.enable.sentry` | `unknown` | `${ENABLE_SENTRY:false}` | `2.1.1.Final` |  |
| `registry.events.kafka.topic` | `optional<string>` | `` | `2.0.0.Final` |  |
| `registry.events.kafka.topic-partition` | `optional<integer>` | `` | `2.0.0.Final` |  |
| `registry.events.ksink` | `optional<string>` | `` | `2.0.0.Final` |  |
| `registry.id` | `unknown` | `apicurio-registry` | `2.2.6-SNAPSHOT` |  |
| `registry.import.url` | `optional<url>` | `` | `2.1.0.Final` |  |
| `registry.keycloak.realm` | `unknown` | `${KEYCLOAK_REALM:apicurio-local}` | `2.0.0.Final` |  |
| `registry.keycloak.url` | `unknown` | `${KEYCLOAK_URL:http://localhost:8090/auth}` | `2.0.0.Final` |  |
| `registry.limits.config.cache.check-period` | `long` | `30000` | `2.1.0.Final` |  |
| `registry.limits.config.max-artifact-labels` | `long` | `-1` | `2.2.3.Final` |  |
| `registry.limits.config.max-artifact-properties` | `long` | `-1` | `2.1.0.Final` |  |
| `registry.limits.config.max-artifacts` | `long` | `-1` | `2.1.0.Final` |  |
| `registry.limits.config.max-description-length` | `long` | `-1` | `2.1.0.Final` |  |
| `registry.limits.config.max-label-size` | `long` | `-1` | `2.1.0.Final` |  |
| `registry.limits.config.max-name-length` | `long` | `-1` | `2.1.0.Final` |  |
| `registry.limits.config.max-property-key-size` | `long` | `-1` | `2.1.0.Final` |  |
| `registry.limits.config.max-property-value-size` | `long` | `-1` | `2.1.0.Final` |  |
| `registry.limits.config.max-requests-per-second` | `long` | `-1` | `2.2.3.Final` |  |
| `registry.limits.config.max-schema-size-bytes` | `long` | `-1` | `2.2.3.Final` |  |
| `registry.limits.config.max-total-schemas` | `long` | `-1` | `2.1.0.Final` |  |
| `registry.limits.config.max-versions-per-artifact` | `long` | `-1` | `2.1.0.Final` |  |
| `registry.liveness.errors.ignored` | `optional<list<string>>` | `` | `1.2.3.Final` |  |
| `registry.metrics.PersistenceExceptionLivenessCheck.counterResetWindowDurationSec` | `integer` | `60` | `1.0.2.Final` |  |
| `registry.metrics.PersistenceExceptionLivenessCheck.disableLogging` | `boolean` | `false` | `2.0.0.Final` |  |
| `registry.metrics.PersistenceExceptionLivenessCheck.errorThreshold` | `integer` | `1` | `1.0.2.Final` |  |
| `registry.metrics.PersistenceExceptionLivenessCheck.statusResetWindowDurationSec` | `integer` | `300` | `1.0.2.Final` |  |
| `registry.metrics.PersistenceTimeoutReadinessCheck.counterResetWindowDurationSec` | `integer` | `60` | `1.0.2.Final` |  |
| `registry.metrics.PersistenceTimeoutReadinessCheck.errorThreshold` | `integer` | `5` | `1.0.2.Final` |  |
| `registry.metrics.PersistenceTimeoutReadinessCheck.statusResetWindowDurationSec` | `integer` | `300` | `1.0.2.Final` |  |
| `registry.metrics.PersistenceTimeoutReadinessCheck.timeoutSec` | `integer` | `15` | `1.0.2.Final` |  |
| `registry.metrics.ResponseErrorLivenessCheck.counterResetWindowDurationSec` | `integer` | `60` | `1.0.2.Final` |  |
| `registry.metrics.ResponseErrorLivenessCheck.disableLogging` | `boolean` | `false` | `2.0.0.Final` |  |
| `registry.metrics.ResponseErrorLivenessCheck.errorThreshold` | `integer` | `1` | `1.0.2.Final` |  |
| `registry.metrics.ResponseErrorLivenessCheck.statusResetWindowDurationSec` | `integer` | `300` | `1.0.2.Final` |  |
| `registry.metrics.ResponseTimeoutReadinessCheck.counterResetWindowDurationSec` | `integer` | `60` | `1.0.2.Final` |  |
| `registry.metrics.ResponseTimeoutReadinessCheck.errorThreshold` | `integer` | `1` | `1.0.2.Final` |  |
| `registry.metrics.ResponseTimeoutReadinessCheck.statusResetWindowDurationSec` | `integer` | `300` | `1.0.2.Final` |  |
| `registry.metrics.ResponseTimeoutReadinessCheck.timeoutSec` | `integer` | `10` | `1.0.2.Final` |  |
| `registry.multitenancy.authorization.enabled` | `boolean` | `true` | `2.1.0.Final` |  |
| `registry.multitenancy.reaper.every` | `optional<string>` | `` | `2.1.0.Final` |  |
| `registry.multitenancy.reaper.max-tenants-reaped` | `int` | `100` | `2.1.0.Final` |  |
| `registry.multitenancy.reaper.period-seconds` | `long` | `10800` | `2.1.0.Final` |  |
| `registry.multitenancy.types.context-path.base-path` | `string` | `t` | `2.1.0.Final` |  |
| `registry.multitenancy.types.context-path.enabled` | `boolean` | `true` | `2.1.0.Final` |  |
| `registry.multitenancy.types.request-header.enabled` | `boolean` | `true` | `2.1.0.Final` |  |
| `registry.multitenancy.types.request-header.name` | `string` | `X-Registry-Tenant-Id` | `2.1.0.Final` |  |
| `registry.multitenancy.types.subdomain.enabled` | `boolean` | `false` | `2.1.0.Final` |  |
| `registry.multitenancy.types.subdomain.header-name` | `string` | `Host` | `2.1.0.Final` |  |
| `registry.multitenancy.types.subdomain.location` | `string` | `header` | `2.1.0.Final` |  |
| `registry.multitenancy.types.subdomain.pattern` | `string` | `(\w[\w\d\-]*)\.localhost\.local` | `2.1.0.Final` |  |
| `registry.name` | `unknown` | `Apicurio Registry (In Memory)` | `2.0.0.Final` |  |
| `registry.organization-id.claim-name` | `list<string>` | `${ORGANIZATION_ID_CLAIM:rh-org-id}` | `2.1.0.Final` |  |
| `registry.redirects` | `map<string, string>` | `` | `2.1.2.Final` |  |
| `registry.redirects.root` | `unknown` | `/,${REGISTRY_ROOT_REDIRECT:/ui/}` | `2.2.6-SNAPSHOT` |  |
| `registry.rest.artifact.download.maxSize` | `int` | `1000000` | `2.2.6-SNAPSHOT` |  |
| `registry.rest.artifact.download.skipSSLValidation` | `boolean` | `false` | `2.2.6-SNAPSHOT` |  |
| `registry.sql.init` | `boolean` | `true` | `2.0.0.Final` |  |
| `registry.storage.metrics.cache.check-period` | `long` | `30000` | `2.1.0.Final` |  |
| `registry.tenant.manager.auth.client-id` | `optional<string>` | `${TENANT_MANAGER_CLIENT_ID:registry-api}` | `2.1.0.Final` |  |
| `registry.tenant.manager.auth.client-secret` | `optional<string>` | `${TENANT_MANAGER_CLIENT_SECRET:default_secret}` | `2.1.0.Final` |  |
| `registry.tenant.manager.auth.enabled` | `optional<boolean>` | `${TENANT_MANAGER_AUTH_ENABLED:${registry.auth.enabled}}` | `2.1.0.Final` |  |
| `registry.tenant.manager.auth.realm` | `unknown` | `${TENANT_MANAGER_REALM:registry}` | `2.2.6-SNAPSHOT` |  |
| `registry.tenant.manager.auth.token.expiration.reduction.ms` | `optional<long>` | `${TENANT_MANAGER_AUTH_TOKEN_EXP_REDUCTION_MS:}` | `2.2.0.Final` |  |
| `registry.tenant.manager.auth.url` | `unknown` | `${TENANT_MANAGER_AUTH_URL:http://localhost:8090/auth}` | `2.2.6-SNAPSHOT` |  |
| `registry.tenant.manager.auth.url.configured` | `optional<string>` | `${TENANT_MANAGER_TOKEN_ENDPOINT:${registry.tenant.manager.auth.url}/realms/${registry.tenant.manager.auth.realm}/protocol/openid-connect/token}` | `2.1.0.Final` |  |
| `registry.tenant.manager.ssl.ca.path` | `optional<string>` | `` | `2.2.0.Final` |  |
| `registry.tenant.manager.url` | `optional<string>` | `${TENANT_MANAGER_URL:http://localhost:8585}` | `2.0.0.Final` |  |
| `registry.tenants.context.cache.check-period` | `long` | `60000` | `2.1.0.Final` |  |
| `registry.ui.config.apiUrl` | `string` | `` | `1.3.0.Final` |  |
| `registry.ui.config.auth.keycloak.clientId` | `unknown` | `${KEYCLOAK_UI_CLIENT_ID:apicurio-registry}` | `2.2.6-SNAPSHOT` |  |
| `registry.ui.config.auth.keycloak.onLoad` | `unknown` | `login-required` | `2.2.6-SNAPSHOT` |  |
| `registry.ui.config.auth.keycloak.realm` | `unknown` | `${registry.keycloak.realm}` | `2.2.6-SNAPSHOT` |  |
| `registry.ui.config.auth.keycloak.url` | `unknown` | `${registry.keycloak.url}` | `2.2.6-SNAPSHOT` |  |
| `registry.ui.config.auth.oidc.client-id` | `string` | `${REGISTRY_OIDC_UI_CLIENT_ID:default_client}` | `2.2.6-SNAPSHOT` |  |
| `registry.ui.config.auth.oidc.redirect-url` | `string` | `${REGISTRY_OIDC_UI_REDIRECT_URL:http://localhost:8080}` | `2.2.6-SNAPSHOT` |  |
| `registry.ui.config.auth.oidc.url` | `string` | `${REGISTRY_AUTH_URL_CONFIGURED:http://localhost:8090}` | `2.2.6-SNAPSHOT` |  |
| `registry.ui.config.auth.type` | `string` | `${REGISTRY_UI_AUTH_TYPE:none}` | `2.2.6-SNAPSHOT` |  |
| `registry.ui.config.uiContextPath` | `string` | `/ui/` | `2.1.0.Final` |  |
| `registry.ui.features.readOnly` | `boolean [dynamic]` | `${REGISTRY_UI_FEATURES_READONLY:false}` | `1.2.0.Final` | When selected, the Service Registry web console is set to read-only, preventing create, read, update, or delete operations. |
| `registry.ui.features.readOnly.dynamic.allow` | `unknown` | `${registry.config.dynamic.allow-all}` | `2.2.6-SNAPSHOT` |  |
| `registry.ui.features.settings` | `boolean` | `true` | `2.2.2.Final` |  |
| `registry.version` | `unknown` | `${project.version}` | `2.0.0.Final` |  |
