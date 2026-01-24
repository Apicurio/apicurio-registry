package io.apicurio.registry.flink;

/**
 * Configuration for ApicurioCatalog.
 */
public final class CatalogConfig {

    /** The catalog name. */
    private final String name;

    /** The default database. */
    private final String defaultDatabase;

    /** The registry URL. */
    private final String url;

    /** The auth type. */
    private final String authType;

    /** The username. */
    private final String username;

    /** The password. */
    private final String password;

    /** The bearer token. */
    private final String token;

    /** OAuth2 token endpoint. */
    private final String tokenEndpoint;

    /** OAuth2 client ID. */
    private final String clientId;

    /** OAuth2 client secret. */
    private final String clientSecret;

    /** The cache TTL in ms. */
    private final long cacheTtlMs;

    CatalogConfig(final Builder b) {
        this.name = b.bName;
        this.defaultDatabase = b.bDefaultDatabase;
        this.url = b.bUrl;
        this.authType = b.bAuthType;
        this.username = b.bUsername;
        this.password = b.bPassword;
        this.token = b.bToken;
        this.tokenEndpoint = b.bTokenEndpoint;
        this.clientId = b.bClientId;
        this.clientSecret = b.bClientSecret;
        this.cacheTtlMs = b.bCacheTtlMs;
    }

    /**
     * Gets the catalog name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the default database.
     *
     * @return the default database
     */
    public String getDefaultDatabase() {
        return defaultDatabase;
    }

    /**
     * Gets the registry URL.
     *
     * @return the URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Gets the auth type.
     *
     * @return the auth type
     */
    public String getAuthType() {
        return authType;
    }

    /**
     * Gets the username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets the password.
     *
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Gets the bearer token.
     *
     * @return the token
     */
    public String getToken() {
        return token;
    }

    /**
     * Gets the OAuth2 token endpoint.
     *
     * @return the endpoint
     */
    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    /**
     * Gets the OAuth2 client ID.
     *
     * @return the client ID
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Gets the OAuth2 client secret.
     *
     * @return the client secret
     */
    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * Gets the cache TTL in milliseconds.
     *
     * @return the TTL
     */
    public long getCacheTtlMs() {
        return cacheTtlMs;
    }

    /**
     * Creates a new builder.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for CatalogConfig.
     */
    public static final class Builder {

        /** The catalog name. */
        private String bName;

        /** The default database. */
        private String bDefaultDatabase = "default";

        /** The registry URL. */
        private String bUrl;

        /** The auth type. */
        private String bAuthType = "none";

        /** The username. */
        private String bUsername;

        /** The password. */
        private String bPassword;

        /** The bearer token. */
        private String bToken;

        /** OAuth2 token endpoint. */
        private String bTokenEndpoint;

        /** OAuth2 client ID. */
        private String bClientId;

        /** OAuth2 client secret. */
        private String bClientSecret;

        /** The cache TTL in milliseconds. */
        private long bCacheTtlMs = ApicurioCatalogOptions.DEFAULT_CACHE_TTL_MS;

        Builder() {
        }

        /**
         * Sets catalog name.
         *
         * @param val the value
         * @return this builder
         */
        public Builder name(final String val) {
            this.bName = val;
            return this;
        }

        /**
         * Sets default database.
         *
         * @param val the value
         * @return this builder
         */
        public Builder defaultDatabase(final String val) {
            this.bDefaultDatabase = val;
            return this;
        }

        /**
         * Sets registry URL.
         *
         * @param val the value
         * @return this builder
         */
        public Builder url(final String val) {
            this.bUrl = val;
            return this;
        }

        /**
         * Sets auth type.
         *
         * @param val the value
         * @return this builder
         */
        public Builder authType(final String val) {
            this.bAuthType = val;
            return this;
        }

        /**
         * Sets username.
         *
         * @param val the value
         * @return this builder
         */
        public Builder username(final String val) {
            this.bUsername = val;
            return this;
        }

        /**
         * Sets password.
         *
         * @param val the value
         * @return this builder
         */
        public Builder password(final String val) {
            this.bPassword = val;
            return this;
        }

        /**
         * Sets bearer token.
         *
         * @param val the value
         * @return this builder
         */
        public Builder token(final String val) {
            this.bToken = val;
            return this;
        }

        /**
         * Sets OAuth2 token endpoint.
         *
         * @param val the value
         * @return this builder
         */
        public Builder tokenEndpoint(final String val) {
            this.bTokenEndpoint = val;
            return this;
        }

        /**
         * Sets OAuth2 client ID.
         *
         * @param val the value
         * @return this builder
         */
        public Builder clientId(final String val) {
            this.bClientId = val;
            return this;
        }

        /**
         * Sets OAuth2 client secret.
         *
         * @param val the value
         * @return this builder
         */
        public Builder clientSecret(final String val) {
            this.bClientSecret = val;
            return this;
        }

        /**
         * Sets cache TTL in ms.
         *
         * @param val the value
         * @return this builder
         */
        public Builder cacheTtlMs(final long val) {
            this.bCacheTtlMs = val;
            return this;
        }

        /**
         * Builds the config.
         *
         * @return the config
         */
        public CatalogConfig build() {
            return new CatalogConfig(this);
        }
    }
}
