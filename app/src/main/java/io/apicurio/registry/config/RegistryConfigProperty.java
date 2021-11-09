/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.config;

import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;

/**
 * @author eric.wittmann@gmail.com
 */
public enum RegistryConfigProperty {


    ////////////////////////////////////////////////////////////////////////////////
    // The following are Startup/Init related properties.
    ////////////////////////////////////////////////////////////////////////////////

    /**
     * A URL of an Apicurio Registry import file that will be imported on startup.
     */
    REGISTRY_IMPORT_URL("registry.import.url", RegistryConfigPropertyType.ReadOnly, null),
    /**
     * The name of the registry - typically just "apicurio-registry".
     */
    REGISTRY_NAME("registry.name", RegistryConfigPropertyType.ReadOnly, null),
    /**
     * Friendly description of the registry.
     */
    REGISTRY_DESCRIPTION("registry.description", RegistryConfigPropertyType.ReadOnly, null),
    /**
     * The registry version.
     */
    REGISTRY_VERSION("registry.version", RegistryConfigPropertyType.ReadOnly, null),
    /**
     * When this version of the registry was built.
     */
    REGISTRY_DATE("registry.date", RegistryConfigPropertyType.ReadOnly, null),
    /**
     * Whether to enable configured redirects.
     */
    REGISTRY_ENABLE_REDIRECTS("registry.enable-redirects", RegistryConfigPropertyType.ReadOnly, true),
    /**
     * Whether to enable Sentry integration.
     */
    REGISTRY_ENABLE_SENTRY("registry.enable.sentry", RegistryConfigPropertyType.ReadOnly, false),


    ////////////////////////////////////////////////////////////////////////////////
    // The following are Storage related properties.
    ////////////////////////////////////////////////////////////////////////////////

    /**
     * What DB is being used - typically postgresql or h2.
     */
    REGISTRY_QUARKUS_DB_KIND("quarkus.datasource.db-kind", RegistryConfigPropertyType.ReadOnly, "postgresql"),
    /**
     * TODO document this
     */
    REGISTRY_STORAGE_METRICS_CACHE_CHECK_PERIOD("registry.storage.metrics.cache.check-period", RegistryConfigPropertyType.ReadOnly, 30000L),


    ////////////////////////////////////////////////////////////////////////////////
    // The following are Readiness related properties.
    ////////////////////////////////////////////////////////////////////////////////

    /**
     * Maximum number of exceptions raised by artifactStore implementation, as captured by this interceptor, before the readiness check fails.
     */
    REGISTRY_METRICS_READINESS_ERROR_THRESHOLD("registry.metrics.PersistenceTimeoutReadinessCheck.errorThreshold", RegistryConfigPropertyType.ReadOnly, 1),
    /**
     * The counter is reset after some time without errors. To fail the check after 2 errors in a minute, set the threshold to 1 and this configuration option to 60.
     * TODO report the absolute count as a metric?
     */
    REGISTRY_METRICS_READINESS_COUNTER_RESET_WINDOW_DURATION("registry.metrics.PersistenceTimeoutReadinessCheck.counterResetWindowDurationSec", RegistryConfigPropertyType.ReadOnly, 60),
    /**
     * If set to a positive value, reset the readiness status after this time window passes without any further errors.
     */
    REGISTRY_METRICS_READINESS_STATUS_RESET_WINDOW_DURATION("registry.metrics.PersistenceTimeoutReadinessCheck.statusResetWindowDurationSec", RegistryConfigPropertyType.ReadOnly, 300),
    /**
     * Set the operation duration in seconds, after which it's considered an error.
     */
    REGISTRY_METRICS_READINESS_TIMEOUT("registry.metrics.PersistenceTimeoutReadinessCheck.timeoutSec", RegistryConfigPropertyType.ReadOnly, 15),
    /**
     * Enable this property to disable logging in the readiness checker.
     */
    REGISTRY_METRICS_READINESS_DISABLE_LOGGING("registry.metrics.PersistenceTimeoutReadinessCheck.disableLogging", RegistryConfigPropertyType.ReadOnly, false),
    /**
     * Maximum number of requests taking more than {@link ResponseTimeoutReadinessCheck#configTimeoutSec} seconds,
     * before the readiness check fails.
     */
    REGISTRY_METRICS_RESP_TIMEOUT_READINESS_ERROR_THRESHOLD("registry.metrics.ResponseTimeoutReadinessCheck.errorThreshold", RegistryConfigPropertyType.ReadOnly, 1),
    /**
     * The counter is reset after some time without errors. To fail the check after 2 errors in a minute, set the threshold to 1 and this configuration option to 60.
     * TODO report the absolute count as a metric?
     */
    REGISTRY_METRICS_RESP_TIMEOUT_READINESS_COUNTER_RESET_WINDOW_DURATION("registry.metrics.ResponseTimeoutReadinessCheck.counterResetWindowDurationSec", RegistryConfigPropertyType.ReadOnly, 60),
    /**
     * If set to a positive value, reset the readiness status after this time window passes without any further errors.
     */
    REGISTRY_METRICS_RESP_TIMEOUT_READINESS_STATUS_RESET_WINDOW_DURATION("registry.metrics.ResponseTimeoutReadinessCheck.statusResetWindowDurationSec", RegistryConfigPropertyType.ReadOnly, 300),
    /**
     * Set the request duration in seconds, after which it's considered an error.
     * TODO This may be expected on some endpoints. Add a way to ignore those.
     */
    REGISTRY_METRICS_RESP_TIMEOUT_READINESS_TIMEOUT("registry.metrics.ResponseTimeoutReadinessCheck.timeoutSec", RegistryConfigPropertyType.ReadOnly, 15),
    /**
     * Enable this property to disable logging in the readiness checker.
     */
    REGISTRY_METRICS_RESP_TIMEOUT_READINESS_DISABLE_LOGGING("registry.metrics.ResponseTimeoutReadinessCheck.disableLogging", RegistryConfigPropertyType.ReadOnly, false),


    ////////////////////////////////////////////////////////////////////////////////
    // The following are Liveness related properties.
    ////////////////////////////////////////////////////////////////////////////////

    /**
     * Maximum number of exceptions raised by artifactStore implementation, as captured by this interceptor, before the liveness check fails.
     */
    REGISTRY_METRICS_EXCEPTION_LIVENESS_ERROR_THRESHOLD("registry.metrics.PersistenceExceptionLivenessCheck.errorThreshold", RegistryConfigPropertyType.ReadOnly, 1),
    /**
     * The counter is reset after some time without errors. To fail the check after 2 errors in a minute, set the threshold to 1 and this configuration option to 60.
     * TODO report the absolute count as a metric?
     */
    REGISTRY_METRICS_EXCEPTION_LIVENESS_COUNTER_RESET_WINDOW_DURATION("registry.metrics.PersistenceExceptionLivenessCheck.counterResetWindowDurationSec", RegistryConfigPropertyType.ReadOnly, 60),
    /**
     * If set to a positive value, reset the liveness status after this time window passes without any further errors.
     */
    REGISTRY_METRICS_EXCEPTION_LIVENESS_STATUS_RESET_WINDOW_DURATION("registry.metrics.PersistenceExceptionLivenessCheck.statusResetWindowDurationSec", RegistryConfigPropertyType.ReadOnly, 300),
    /**
     * Use this property to disable logging in the liveness checker.
     */
    REGISTRY_METRICS_EXCEPTION_LIVENESS_DISABLE_LOGGING("registry.metrics.PersistenceExceptionLivenessCheck.disableLogging", RegistryConfigPropertyType.ReadOnly, false),
    /**
     * Maximum number of HTTP 5xx errors returned to the user
     * as captured by {@link io.apicurio.registry.rest.RegistryExceptionMapper}
     * before the liveness check fails.
     */
    REGISTRY_METRICS_RESP_ERROR_LIVENESS_ERROR_THRESHOLD("registry.metrics.ResponseErrorLivenessCheck.errorThreshold", RegistryConfigPropertyType.ReadOnly, 1),
    /**
     * The counter is reset after some time without errors.  To fail the check after 2 errors in a minute, set the threshold to 1 and this configuration option to 60.
     * TODO report the absolute count as a metric?
     */
    REGISTRY_METRICS_RESP_ERROR_LIVENESS_COUNTER_RESET_WINDOW_DURATION("registry.metrics.ResponseErrorLivenessCheck.counterResetWindowDurationSec", RegistryConfigPropertyType.ReadOnly, 60),
    /**
     * If set to a positive value, reset the liveness status after this time window passes without any further errors.
     */
    REGISTRY_METRICS_RESP_ERROR_LIVENESS_STATUS_RESET_WINDOW_DURATION("registry.metrics.ResponseErrorLivenessCheck.statusResetWindowDurationSec", RegistryConfigPropertyType.ReadOnly, 300),
    /**
     * Use this property to disable logging in the liveness checker.
     */
    REGISTRY_METRICS_RESP_ERROR_LIVENESS_DISABLE_LOGGING("registry.metrics.ResponseErrorLivenessCheck.disableLogging", RegistryConfigPropertyType.ReadOnly, false),


    ////////////////////////////////////////////////////////////////////////////////
    // The following are authorization related properties.
    ////////////////////////////////////////////////////////////////////////////////

    /**
     * Indicates whether Quarkus OIDC is enabled.
     */
    REGISTRY_OIDC_TENANT_ENABLED("quarkus.oidc.tenant-enabled", RegistryConfigPropertyType.ReadOnly, false),
    /**
     * Enable/disable authorization.
     */
    REGISTRY_AUTH_ENABLED("registry.auth.enabled", RegistryConfigPropertyType.ReadOnly, false),
    /**
     * Enable role based authorization.
     */
    REGISTRY_AUTH_ROLE_BASED_AUTHORIZATION("registry.auth.role-based-authorization", RegistryConfigPropertyType.ReadOnly, false),
    /**
     * Enable owner-only authorization, which means that artifact editing/deleting will be limited to only the user
     * who created the artifact.
     */
    REGISTRY_AUTH_OWNER_ONLY_AUTHORIZATION("registry.auth.owner-only-authorization", RegistryConfigPropertyType.Editable, false),
    /**
     * Enable anonymous read access to artifacts, which means that UNAUTHENTICATED clients will be able to access
     * artifact information (content and meta-data) but not modify it.
     */
    REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED("registry.auth.anonymous-read-access.enabled", RegistryConfigPropertyType.Editable, false),
    /**
     * Enable group access limits based on ownership.  Must be used in combination with REGISTRY_AUTH_OWNER_ONLY_AUTHORIZATION.
     */
    REGISTRY_AUTH_OBAC_LIMIT_GROUP_ACCESS("registry.auth.owner-only-authorization.limit-group-access", RegistryConfigPropertyType.Editable, false),
    /**
     * Set the name of the role that represents read-only access.
     */
    REGISTRY_AUTH_ROLES_READONLY("registry.auth.roles.readonly", RegistryConfigPropertyType.ReadOnly, "sr-readonly"),
    /**
     * Set the name of the role that represents developer (read/write) access.
     */
    REGISTRY_AUTH_ROLES_DEVELOPER("registry.auth.roles.developer", RegistryConfigPropertyType.ReadOnly, "sr-developer"),
    /**
     * Set the name of the role that represents admin access.
     */
    REGISTRY_AUTH_ROLES_ADMIN("registry.auth.roles.admin", RegistryConfigPropertyType.ReadOnly, "sr-admin"),
    /**
     * Set the source for role information:
     *   - token: roles will be extracted from the JWT
     *   - application: roles will be managed by the application (in the DB and via the REST API)
     */
    REGISTRY_AUTH_ROLE_SOURCE("registry.auth.role-source", RegistryConfigPropertyType.ReadOnly, "token"),
    /**
     * Enable an auth feature whereby the owner of a tenant (in a multi-tenant deployment) is automatically
     * granted Admin role, regardless of that user's JWT or application roles.
     */
    REGISTRY_AUTH_TENANT_OWNER_IS_ADMIN("registry.auth.tenant-owner-is-admin.enabled", RegistryConfigPropertyType.ReadOnly, true),
    /**
     * Enable the "admin override" feature, which allows certain conditions to result in an authenticated user
     * having the Admin role, regardless of their JWT or application managed roles.  This is typically used when
     * enabling application managed RBAC in order to grant certain users Admin privileges without first adding
     * an Admin role in the DB.
     */
    REGISTRY_AUTH_ADMIN_OVERRIDE_ENABLED("registry.auth.admin-override.enabled", RegistryConfigPropertyType.ReadOnly, false),
    /**
     * Indicates where the admin override information will come from.  For example "token" indicates the info will
     * come from the JWT.
     */
    REGISTRY_AUTH_ADMIN_OVERRIDE_FROM("registry.auth.admin-override.from", RegistryConfigPropertyType.ReadOnly, "token"),
    /**
     * Indicates the type of information (e.g. "role" or "claim") that will provide the insight into whether
     * the user is an Admin.
     */
    REGISTRY_AUTH_ADMIN_OVERRIDE_TYPE("registry.auth.admin-override.type", RegistryConfigPropertyType.ReadOnly, "role"),
    /**
     * When the admin-override type is "role", this controls the name of the role that indicates the user is an Admin.
     */
    REGISTRY_AUTH_ADMIN_OVERRIDE_ROLE("registry.auth.admin-override.role", RegistryConfigPropertyType.ReadOnly, "sr-admin"),
    /**
     * When the admin-override type is "claim", this controls the name of the JWT claim that will contain a value that
     * indicates whether the user is an admin.
     */
    REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM("registry.auth.admin-override.claim", RegistryConfigPropertyType.ReadOnly, "org-admin"),
    /**
     * When the admin-override type is "claim", this controls the value that the named JWT claim must have in order for
     * the user to be considered an admin.
     */
    REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM_VALUE("registry.auth.admin-override.claim-value", RegistryConfigPropertyType.ReadOnly, true),
    /**
     * Enable to allow BASIC authentication in addition to token based auth.  If enabled this will cause the server to
     * perform client credentials OAuth flow on behalf of the user.
     */
    REGISTRY_AUTH_BASIC_AUTH_CLIENT_CREDENTIALS_ENABLED("registry.auth.basic-auth-client-credentials.enabled", RegistryConfigPropertyType.Editable, false),
    /**
     * URL to the OAuth token endpoint to use for performing client-credentials OAuth flow on behalf of a user when
     * BASIC auth is enabled.
     */
    REGISTRY_AUTH_TOKEN_ENDPOINT("registry.auth.token.endpoint", RegistryConfigPropertyType.ReadOnly, null),
    /**
     * TODO need description here from @Carles
     */
    REGISTRY_AUTH_OIDC_CLIENT_SECRECT("registry.auth.client-secret", RegistryConfigPropertyType.ReadOnly, null),
    /**
     * TODO need description here from @Carles
     */
    REGISTRY_AUTH_OIDC_CLIENT_ID("quarkus.oidc.client-id", RegistryConfigPropertyType.ReadOnly, null),


    ////////////////////////////////////////////////////////////////////////////////
    // The following are storage related properties.
    ////////////////////////////////////////////////////////////////////////////////

    /**
     * Enable to initialize the DB on startup if it is not yet initialized.
     */
    REGISTRY_SQL_INIT("registry.sql.init", RegistryConfigPropertyType.ReadOnly, true),
    /**
     * Enable to initialize the DB on startup if it is not yet initialized.
     */
    REGISTRY_SQL_JDBC_URL("quarkus.datasource.jdbc.url", RegistryConfigPropertyType.ReadOnly, null),


    ////////////////////////////////////////////////////////////////////////////////
    // The following are REST related properties.
    ////////////////////////////////////////////////////////////////////////////////

    /**
     * How long a download link (from the /downloads rest endpoint) should be valid before expiring.  Specify
     * a number of seconds.
     */
    REGISTRY_REST_DOWNLOAD_HREF_TTL("registry.download.href.ttl", RegistryConfigPropertyType.Editable, 30L),
    /**
     * Enable so that the CCompat API uses globalId instead of contentId to maintain legacy compatibility between
     * Apicurio and Confluent clients.
     */
    REGISTRY_CCOMPAT_LEGACY_ID_MODE_ENABLED("registry.ccompat.legacy-id-mode.enabled", RegistryConfigPropertyType.Editable, false),


    ////////////////////////////////////////////////////////////////////////////////
    // The following are Events related properties.
    ////////////////////////////////////////////////////////////////////////////////

    /**
     * The topic to use in the kafka events sink.
     */
    REGISTRY_EVENTS_KAFKA_TOPIC("registry.events.kafka.topic", RegistryConfigPropertyType.ReadOnly, null),
    /**
     * The topic partition to use in the kafka events sink.
     */
    REGISTRY_EVENTS_KAFKA_TOPIC_PARTITION("registry.events.kafka.topic-partition", RegistryConfigPropertyType.ReadOnly, null),
    /**
     * Used to configure the HttpEventSink.  TODO : add docs
     */
    REGISTRY_EVENTS_KSINK("registry.events.ksink", RegistryConfigPropertyType.ReadOnly, null),


    ////////////////////////////////////////////////////////////////////////////////
    // The following are Multitenancy related properties.
    // TODO document the multi-tenancy properties
    ////////////////////////////////////////////////////////////////////////////////

    REGISTRY_MULTITENANCY_ENABLED("registry.enable.multitenancy", RegistryConfigPropertyType.ReadOnly, false),
    REGISTRY_MULTITENANCY_AUTHORIZATION_ENABLED("registry.multitenancy.authorization.enabled", RegistryConfigPropertyType.ReadOnly, true),
    REGISTRY_MULTITENANCY_CONTEXT_PATH_ENABLED("registry.multitenancy.types.context-path.enabled", RegistryConfigPropertyType.ReadOnly, true),
    REGISTRY_MULTITENANCY_SUBDOMAIN_ENABLED("registry.multitenancy.types.subdomain.enabled", RegistryConfigPropertyType.ReadOnly, true),
    REGISTRY_MULTITENANCY_REQUEST_HEADER_ENABLED("registry.multitenancy.types.request-header.enabled", RegistryConfigPropertyType.ReadOnly, true),
    REGISTRY_MULTITENANCY_CONTEXT_PATH_BASE_PATH("registry.multitenancy.types.context-path.base-path", RegistryConfigPropertyType.ReadOnly, "t"),
    REGISTRY_MULTITENANCY_SUBDOMAIN_LOCATION("registry.multitenancy.types.subdomain.location", RegistryConfigPropertyType.ReadOnly, "header"),
    REGISTRY_MULTITENANCY_SUBDOMAIN_HEADER_NAME("registry.multitenancy.types.subdomain.header-name", RegistryConfigPropertyType.ReadOnly, "Host"),
    REGISTRY_MULTITENANCY_SUBDOMAIN_PATTERN("registry.multitenancy.types.subdomain.pattern", RegistryConfigPropertyType.ReadOnly, "(\\w[\\w\\d\\-]*)\\.localhost\\.local"),
    REGISTRY_MULTITENANCY_REQUEST_HEADER_NAME("registry.multitenancy.types.request-header.name", RegistryConfigPropertyType.ReadOnly, "X-Registry-Tenant-Id"),
    REGISTRY_MULTITENANCY_REAPER_EVERY("registry.multitenancy.reaper.every", RegistryConfigPropertyType.ReadOnly, null),
    REGISTRY_MULTITENANCY_REAPER_PERIOD("registry.multitenancy.reaper.period-seconds", RegistryConfigPropertyType.ReadOnly, 10800L),
    REGISTRY_MULTITENANCY_TENANT_MANAGER_URL("registry.tenant.manager.url", RegistryConfigPropertyType.ReadOnly, null),
    REGISTRY_MULTITENANCY_TENANT_MANAGER_AUTH_ENABLED("registry.tenant.manager.auth.enabled", RegistryConfigPropertyType.ReadOnly, false),
    REGISTRY_MULTITENANCY_TENANT_MANAGER_AUTH_URL("registry.tenant.manager.auth.url.configured", RegistryConfigPropertyType.ReadOnly, null),
    REGISTRY_MULTITENANCY_TENANT_MANAGER_AUTH_CLIENT_ID("registry.tenant.manager.auth.client-id", RegistryConfigPropertyType.ReadOnly, null),
    REGISTRY_MULTITENANCY_TENANT_MANAGER_AUTH_CLIENT_SECRET("registry.tenant.manager.auth.client-secret", RegistryConfigPropertyType.ReadOnly, null),
    REGISTRY_TENANTS_CONTEXT_CACHE_CHECK_PERIOD("registry.tenants.context.cache.check-period", RegistryConfigPropertyType.ReadOnly, 60000L),
    REGISTRY_TENANTS_ORGANIZATION_CLAIM_NAME("registry.organization-id.claim-name", RegistryConfigPropertyType.ReadOnly, "rh-org-id"),
    REGISTRY_MULTITENANCY_REAPER_MAX_TENANTS_REAPED("registry.multitenancy.reaper.max-tenants-reaped", RegistryConfigPropertyType.ReadOnly, 100),


    ////////////////////////////////////////////////////////////////////////////////
    // The following are Limits related properties.
    // TODO document the Limits properties.
    ////////////////////////////////////////////////////////////////////////////////

    REGISTRY_LIMITS_CONFIG_CACHE_CHECK_PERIOD("registry.limits.config.cache.check-period", RegistryConfigPropertyType.ReadOnly, 30000L),
    REGISTRY_LIMITS_DEFAULT_MAX_TOTAL_SCHEMAS("registry.limits.config.max-total-schemas", RegistryConfigPropertyType.ReadOnly, -1L),
    REGISTRY_LIMITS_DEFAULT_MAX_ARTIFACTS("registry.limits.config.max-artifacts", RegistryConfigPropertyType.ReadOnly, -1L),
    REGISTRY_LIMITS_DEFAULT_MAX_VERSIONS_PER_ARTIFACT("registry.limits.config.max-versions-per-artifact", RegistryConfigPropertyType.ReadOnly, -1L),
    REGISTRY_LIMITS_DEFAULT_MAX_ARTIFACT_PROPS("registry.limits.config.max-artifact-properties", RegistryConfigPropertyType.ReadOnly, -1L),
    REGISTRY_LIMITS_DEFAULT_MAX_PROPERTY_KEY_BYTES("registry.limits.config.max-property-key-size", RegistryConfigPropertyType.ReadOnly, -1L),
    REGISTRY_LIMITS_DEFAULT_MAX_PROPERTY_VALUE_BYTES("registry.limits.config.max-property-value-size", RegistryConfigPropertyType.ReadOnly, -1L),
    REGISTRY_LIMITS_DEFAULT_MAX_ARTIFACT_LABELS("registry.limits.config.max-artifact-labels", RegistryConfigPropertyType.ReadOnly, -1L),
    REGISTRY_LIMITS_DEFAULT_MAX_LABEL_SIZE("registry.limits.config.max-label-size", RegistryConfigPropertyType.ReadOnly, -1L),
    REGISTRY_LIMITS_DEFAULT_MAX_NAME_LENGTH("registry.limits.config.max-name-length", RegistryConfigPropertyType.ReadOnly, -1L),
    REGISTRY_LIMITS_DEFAULT_MAX_DESCRIPTION_LENGTH("registry.limits.config.max-description-length", RegistryConfigPropertyType.ReadOnly, -1L),


    ////////////////////////////////////////////////////////////////////////////////
    // The following are UI related properties.
    ////////////////////////////////////////////////////////////////////////////////
    /**
     * Switch the UI into "read-only" mode.
     */
    REGISTRY_UI_FEATURES_READONLY("registry.ui.features.readOnly", RegistryConfigPropertyType.ReadOnly, false),
    /**
     * The relative context path of the UI.  Useful if the UI is relocated from /ui/ to some other path.
     */
    REGISTRY_UI_CONFIG_UI_CONTEXT_PATH("registry.ui.config.uiContextPath", RegistryConfigPropertyType.ReadOnly, "/ui/"),
    /**
     * The URL to the API that the UI should use when making REST calls to the backend.  Useful in some deployment scenarios
     * to force the UI to a specific absolute URL.
     */
    REGISTRY_UI_CONFIG_API_URL("registry.ui.config.apiUrl", RegistryConfigPropertyType.ReadOnly, null),


    ;

    private final String name;
    private final RegistryConfigPropertyType type;
    private final Object defaultValue;

    private RegistryConfigProperty(String name, RegistryConfigPropertyType type, Object defaultValue) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public RegistryConfigPropertyType type() {
        return this.type;
    }

    public boolean isEditable() {
        return this.type == RegistryConfigPropertyType.Editable;
    }

    @Override
    public String toString() {
        return this.name;
    }

    String propertyName() {
        return this.name;
    }

    Object defaultValue() {
        return this.defaultValue;
    }

    public static RegistryConfigProperty fromPropertyName(String propertyName) {
        for (RegistryConfigProperty property : values()) {
            if (property.propertyName().equals(propertyName)) {
                return property;
            }
        }
        return null;
    }

}
