package io.apicurio.registry.cli.config;

// Keys for the properties stored in the "config" map of ConfigModel ($ACR_CURRENT_HOME/config.json).
public final class ConfigProperties {

    private ConfigProperties() {
    }

    // Hidden from the "acr config" listing.
    public static final String INTERNAL_PREFIX = "internal.";

    public static final String UPDATE_CHECK_ENABLED = "update.check-enabled";
    public static final String UPDATE_TIMEOUT_SECONDS = "update.timeout-seconds";

    public static final String INTERNAL_UPDATE_LAST_CHECK = INTERNAL_PREFIX + "update.last-check";
    public static final String INTERNAL_UPDATE_POSTPONED_UNTIL = INTERNAL_PREFIX + "update.postponed-until";
    public static final String INTERNAL_UPDATE_REPO_URL = INTERNAL_PREFIX + "update.repo-url";
    public static final String INTERNAL_BRANDING_PRODUCT_NAME = INTERNAL_PREFIX + "branding.product-name";

    public static boolean isInternal(final String key) {
        return key != null && key.startsWith(INTERNAL_PREFIX);
    }
}
