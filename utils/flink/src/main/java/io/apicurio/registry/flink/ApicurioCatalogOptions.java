package io.apicurio.registry.flink;

import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ConfigOptions;

/**
 * Configuration options for the Apicurio Registry Flink Catalog.
 */
public final class ApicurioCatalogOptions {

    /** The identifier for the Apicurio catalog factory. */
    public static final String IDENTIFIER = "apicurio";

    /** Default cache TTL in milliseconds (5 minutes). */
    public static final long DEFAULT_CACHE_TTL_MS = 300000L;

    /** The base URL of the Apicurio Registry API. */
    public static final ConfigOption<String> REGISTRY_URL = ConfigOptions
            .key("registry.url")
            .stringType()
            .noDefaultValue()
            .withDescription("Base URL of the Apicurio Registry API.");

    /** Authentication type: none, basic, or bearer. */
    public static final ConfigOption<String> AUTH_TYPE = ConfigOptions
            .key("registry.auth.type")
            .stringType()
            .defaultValue("none")
            .withDescription("Auth type: 'none', 'basic', or 'bearer'.");

    /** Username for basic authentication. */
    public static final ConfigOption<String> AUTH_USERNAME = ConfigOptions
            .key("registry.auth.username")
            .stringType()
            .noDefaultValue()
            .withDescription("Username for basic authentication.");

    /** Password for basic authentication. */
    public static final ConfigOption<String> AUTH_PASSWORD = ConfigOptions
            .key("registry.auth.password")
            .stringType()
            .noDefaultValue()
            .withDescription("Password for basic authentication.");

    /** Bearer token for token-based authentication. */
    public static final ConfigOption<String> AUTH_TOKEN = ConfigOptions
            .key("registry.auth.token")
            .stringType()
            .noDefaultValue()
            .withDescription("Bearer token for token-based auth.");

    /** Cache TTL in milliseconds for schema caching. */
    public static final ConfigOption<Long> CACHE_TTL_MS = ConfigOptions
            .key("cache.ttl.ms")
            .longType()
            .defaultValue(DEFAULT_CACHE_TTL_MS)
            .withDescription("Cache TTL in milliseconds.");

    /** OAuth2 token endpoint URL. */
    public static final ConfigOption<String> AUTH_TOKEN_ENDPOINT = ConfigOptions
            .key("registry.auth.token-endpoint")
            .stringType()
            .noDefaultValue()
            .withDescription("OAuth2 token endpoint URL.");

    /** OAuth2 client ID. */
    public static final ConfigOption<String> AUTH_CLIENT_ID = ConfigOptions
            .key("registry.auth.client-id")
            .stringType()
            .noDefaultValue()
            .withDescription("OAuth2 client ID.");

    /** OAuth2 client secret. */
    public static final ConfigOption<String> AUTH_CLIENT_SECRET = ConfigOptions
            .key("registry.auth.client-secret")
            .stringType()
            .noDefaultValue()
            .withDescription("OAuth2 client secret.");

    /** The default database (group) to use. */
    public static final ConfigOption<String> DEFAULT_DATABASE = ConfigOptions
            .key("default-database")
            .stringType()
            .defaultValue("default")
            .withDescription("The default database (group) to use.");

    private ApicurioCatalogOptions() {
    }
}
