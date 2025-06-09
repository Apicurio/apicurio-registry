package io.apicurio.common.apps.config;

public enum ConfigPropertyCategory {

    CATEGORY_API("api"),
    CATEGORY_AUTH("auth"),
    CATEGORY_CACHE("cache"),
    CATEGORY_CCOMPAT("ccompat"),
    CATEGORY_DOWNLOAD("download"),
    CATEGORY_GITOPS("gitops"),
    CATEGORY_HEALTH("health"),
    /**
     * Properties that belong to this category will not show up in the documentation.
     */
    CATEGORY_HIDDEN("hidden"),
    CATEGORY_IMPORT("import"),
    CATEGORY_LIMITS("limits"),
    CATEGORY_REDIRECTS("redirects"),
    CATEGORY_REST("rest"),
    CATEGORY_SEMVER("semver"),
    CATEGORY_STORAGE("storage"),
    CATEGORY_SYSTEM("system"),
    CATEGORY_UI("ui");

    private final String value;

    ConfigPropertyCategory(String value) {
        this.value = value;
    }

    @SuppressWarnings("unused")
    public String getRawValue() {
        return value;
    }
}
