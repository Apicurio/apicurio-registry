package io.apicurio.registry.flink;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.flink.converter.AvroFlinkTypeConverter;
import io.apicurio.registry.flink.converter.JsonSchemaFlinkTypeConverter;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.SearchedArtifact;
import io.apicurio.registry.rest.client.models.SearchedGroup;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.catalog.AbstractCatalog;
import org.apache.flink.table.catalog.CatalogBaseTable;
import org.apache.flink.table.catalog.CatalogDatabase;
import org.apache.flink.table.catalog.CatalogDatabaseImpl;
import org.apache.flink.table.catalog.CatalogFunction;
import org.apache.flink.table.catalog.CatalogPartition;
import org.apache.flink.table.catalog.CatalogPartitionSpec;
import org.apache.flink.table.catalog.CatalogTable;
import org.apache.flink.table.catalog.Column;
import org.apache.flink.table.catalog.ObjectPath;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.catalog.exceptions.CatalogException;
import org.apache.flink.table.catalog.exceptions.DatabaseAlreadyExistException;
import org.apache.flink.table.catalog.exceptions.DatabaseNotEmptyException;
import org.apache.flink.table.catalog.exceptions.DatabaseNotExistException;
import org.apache.flink.table.catalog.exceptions.FunctionAlreadyExistException;
import org.apache.flink.table.catalog.exceptions.FunctionNotExistException;
import org.apache.flink.table.catalog.exceptions.PartitionAlreadyExistsException;
import org.apache.flink.table.catalog.exceptions.PartitionNotExistException;
import org.apache.flink.table.catalog.exceptions.PartitionSpecInvalidException;
import org.apache.flink.table.catalog.exceptions.TableAlreadyExistException;
import org.apache.flink.table.catalog.exceptions.TableNotExistException;
import org.apache.flink.table.catalog.exceptions.TableNotPartitionedException;
import org.apache.flink.table.catalog.exceptions.TablePartitionedException;
import org.apache.flink.table.catalog.stats.CatalogColumnStatistics;
import org.apache.flink.table.catalog.stats.CatalogTableStatistics;
import org.apache.flink.table.expressions.Expression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flink Catalog implementation backed by Apicurio Registry.
 *
 * <p>
 * Maps Apicurio groups to Flink databases and artifacts to tables.
 */
public final class ApicurioCatalog extends AbstractCatalog {

    /** Logger for this class. */
    private static final Logger LOG =
            LoggerFactory.getLogger(ApicurioCatalog.class);

    /** The catalog configuration. */
    private final CatalogConfig config;

    /** Cache for schemas with TTL. */
    private final Map<String, CachedSchema> schemaCache;

    /** The registry client. */
    private RegistryClient client;

    /**
     * Creates a new ApicurioCatalog.
     *
     * @param catalogConfig the catalog configuration
     */
    public ApicurioCatalog(final CatalogConfig catalogConfig) {
        super(catalogConfig.getName(), catalogConfig.getDefaultDatabase());
        this.config = catalogConfig;
        this.schemaCache = new ConcurrentHashMap<>();
    }

    @Override
    public void open() throws CatalogException {
        LOG.info("Opening catalog: {} at {}", getName(), config.getUrl());
        RegistryClientOptions opts =
                RegistryClientOptions.create(config.getUrl());
        if ("basic".equalsIgnoreCase(config.getAuthType())
                && config.getUsername() != null
                && config.getPassword() != null) {
            opts = opts.basicAuth(config.getUsername(), config.getPassword());
        } else if ("oauth2".equalsIgnoreCase(config.getAuthType())
                && config.getTokenEndpoint() != null
                && config.getClientId() != null
                && config.getClientSecret() != null) {
            opts = opts.oauth2(
                    config.getTokenEndpoint(),
                    config.getClientId(),
                    config.getClientSecret());
        }
        this.client = RegistryClientFactory.create(opts);
        LOG.info("Catalog opened successfully");
    }

    @Override
    public void close() throws CatalogException {
        LOG.info("Closing catalog: {}", getName());
        this.client = null;
        this.schemaCache.clear();
    }

    @Override
    public List<String> listDatabases() throws CatalogException {
        LOG.debug("Listing databases");
        try {
            final List<String> groups = new ArrayList<>();
            final var result = client.groups().get();
            if (result != null && result.getGroups() != null) {
                for (SearchedGroup g : result.getGroups()) {
                    groups.add(g.getGroupId());
                }
            }
            LOG.debug("Found {} databases", groups.size());
            return groups;
        } catch (Exception e) {
            throw new CatalogException("Failed to list databases", e);
        }
    }

    @Override
    public CatalogDatabase getDatabase(final String dbName)
            throws DatabaseNotExistException, CatalogException {
        LOG.debug("Getting database: {}", dbName);
        try {
            final GroupMetaData grp = client.groups().byGroupId(dbName).get();
            if (grp == null) {
                throw new DatabaseNotExistException(getName(), dbName);
            }
            final Map<String, String> props = new HashMap<>();
            if (grp.getDescription() != null) {
                props.put("description", grp.getDescription());
            }
            if (grp.getCreatedOn() != null) {
                props.put("createdOn", grp.getCreatedOn().toString());
            }
            return new CatalogDatabaseImpl(props, grp.getDescription());
        } catch (DatabaseNotExistException e) {
            throw e;
        } catch (Exception e) {
            if (isNotFoundError(e)) {
                throw new DatabaseNotExistException(getName(), dbName);
            }
            throw new CatalogException("Failed to get database", e);
        }
    }

    @Override
    public boolean databaseExists(final String dbName) throws CatalogException {
        try {
            client.groups().byGroupId(dbName).get();
            return true;
        } catch (Exception e) {
            if (isNotFoundError(e)) {
                return false;
            }
            throw new CatalogException("Failed to check database", e);
        }
    }

    @Override
    public void createDatabase(
            final String name,
            final CatalogDatabase db,
            final boolean ignoreIfExists) throws DatabaseAlreadyExistException {
        throw new CatalogException("Create database not supported");
    }

    @Override
    public void dropDatabase(
            final String name,
            final boolean ignoreIfNotExists,
            final boolean cascade) throws DatabaseNotExistException,
            DatabaseNotEmptyException {
        throw new CatalogException("Drop database not supported");
    }

    @Override
    public void alterDatabase(
            final String name,
            final CatalogDatabase newDb,
            final boolean ignoreIfNotExists) throws DatabaseNotExistException {
        throw new CatalogException("Alter database not supported");
    }

    @Override
    public List<String> listTables(final String dbName)
            throws DatabaseNotExistException, CatalogException {
        LOG.debug("Listing tables in: {}", dbName);
        if (!databaseExists(dbName)) {
            throw new DatabaseNotExistException(getName(), dbName);
        }
        try {
            final List<String> tables = new ArrayList<>();
            final var result = client.groups()
                    .byGroupId(dbName).artifacts().get();
            if (result != null && result.getArtifacts() != null) {
                for (SearchedArtifact a : result.getArtifacts()) {
                    tables.add(a.getArtifactId());
                }
            }
            LOG.debug("Found {} tables", tables.size());
            return tables;
        } catch (Exception e) {
            throw new CatalogException("Failed to list tables", e);
        }
    }

    @Override
    public List<String> listViews(final String dbName)
            throws DatabaseNotExistException {
        return Collections.emptyList();
    }

    @Override
    public CatalogBaseTable getTable(final ObjectPath path)
            throws TableNotExistException, CatalogException {
        LOG.debug("Getting table: {}", path);
        final String dbName = path.getDatabaseName();
        final String tblName = path.getObjectName();
        final String cacheKey = dbName + "/" + tblName;
        final CachedSchema cached = schemaCache.get(cacheKey);

        if (cached != null && !cached.isExpired(config.getCacheTtlMs())) {
            LOG.debug("Returning cached table: {}", path);
            return cached.getTable();
        }

        try {
            final ArtifactMetaData meta = client.groups()
                    .byGroupId(dbName)
                    .artifacts()
                    .byArtifactId(tblName)
                    .get();
            if (meta == null) {
                throw new TableNotExistException(getName(), path);
            }

            final InputStream stream = client.groups()
                    .byGroupId(dbName)
                    .artifacts()
                    .byArtifactId(tblName)
                    .versions()
                    .byVersionExpression("branch=latest")
                    .content()
                    .get();

            final String content = new String(
                    stream.readAllBytes(), StandardCharsets.UTF_8);
            final String artType = meta.getArtifactType();
            final ResolvedSchema resolved =
                    convertToFlinkSchema(content, artType);

            final Map<String, String> opts = new HashMap<>();
            opts.put("connector", "apicurio-registry");
            opts.put("registry.url", config.getUrl());
            opts.put("group.id", dbName);
            opts.put("artifact.id", tblName);

            final Schema.Builder builder = Schema.newBuilder();
            for (Column col : resolved.getColumns()) {
                builder.column(col.getName(), col.getDataType());
            }

            final CatalogTable tbl = CatalogTable.newBuilder()
                    .schema(builder.build())
                    .comment(meta.getDescription())
                    .options(opts)
                    .build();

            schemaCache.put(cacheKey,
                    new CachedSchema(tbl, System.currentTimeMillis()));
            return tbl;
        } catch (TableNotExistException e) {
            throw e;
        } catch (Exception e) {
            if (isNotFoundError(e)) {
                throw new TableNotExistException(getName(), path);
            }
            throw new CatalogException("Failed to get table", e);
        }
    }

    @Override
    public boolean tableExists(final ObjectPath path) throws CatalogException {
        try {
            client.groups()
                    .byGroupId(path.getDatabaseName())
                    .artifacts()
                    .byArtifactId(path.getObjectName())
                    .get();
            return true;
        } catch (Exception e) {
            if (isNotFoundError(e)) {
                return false;
            }
            throw new CatalogException("Failed to check table", e);
        }
    }

    @Override
    public void dropTable(
            final ObjectPath path,
            final boolean ignoreIfNotExists) throws TableNotExistException {
        throw new CatalogException("Drop table not supported");
    }

    @Override
    public void renameTable(
            final ObjectPath path,
            final String newName,
            final boolean ignoreIfNotExists) throws TableNotExistException,
            TableAlreadyExistException {
        throw new CatalogException("Rename table not supported");
    }

    @Override
    public void createTable(
            final ObjectPath path,
            final CatalogBaseTable tbl,
            final boolean ignoreIfExists) throws TableAlreadyExistException,
            DatabaseNotExistException {
        throw new CatalogException("Create table not supported");
    }

    @Override
    public void alterTable(
            final ObjectPath path,
            final CatalogBaseTable newTbl,
            final boolean ignoreIfNotExists) throws TableNotExistException {
        throw new CatalogException("Alter table not supported");
    }

    @Override
    public List<String> listFunctions(final String dbName)
            throws DatabaseNotExistException {
        return Collections.emptyList();
    }

    @Override
    public CatalogFunction getFunction(final ObjectPath funcPath)
            throws FunctionNotExistException {
        throw new FunctionNotExistException(getName(), funcPath);
    }

    @Override
    public boolean functionExists(final ObjectPath funcPath) {
        return false;
    }

    @Override
    public void createFunction(
            final ObjectPath funcPath,
            final CatalogFunction func,
            final boolean ignoreIfExists) throws FunctionAlreadyExistException,
            DatabaseNotExistException {
        throw new CatalogException("Functions not supported");
    }

    @Override
    public void alterFunction(
            final ObjectPath funcPath,
            final CatalogFunction newFunc,
            final boolean ignoreIfNotExists) throws FunctionNotExistException {
        throw new CatalogException("Functions not supported");
    }

    @Override
    public void dropFunction(
            final ObjectPath funcPath,
            final boolean ignoreIfNotExists) throws FunctionNotExistException {
        throw new CatalogException("Functions not supported");
    }

    @Override
    public List<CatalogPartitionSpec> listPartitions(final ObjectPath path)
            throws TableNotExistException, TableNotPartitionedException {
        return Collections.emptyList();
    }

    @Override
    public List<CatalogPartitionSpec> listPartitions(
            final ObjectPath path,
            final CatalogPartitionSpec spec)
            throws TableNotExistException, TableNotPartitionedException,
            PartitionSpecInvalidException {
        return Collections.emptyList();
    }

    @Override
    public List<CatalogPartitionSpec> listPartitionsByFilter(
            final ObjectPath path,
            final List<Expression> filters) throws TableNotExistException,
            TableNotPartitionedException {
        return Collections.emptyList();
    }

    @Override
    public CatalogPartition getPartition(
            final ObjectPath path,
            final CatalogPartitionSpec spec)
            throws PartitionNotExistException {
        throw new PartitionNotExistException(getName(), path, spec);
    }

    @Override
    public boolean partitionExists(
            final ObjectPath path,
            final CatalogPartitionSpec spec) {
        return false;
    }

    @Override
    public void createPartition(
            final ObjectPath path,
            final CatalogPartitionSpec spec,
            final CatalogPartition part,
            final boolean ignoreIfExists) throws TableNotExistException,
            TableNotPartitionedException, PartitionSpecInvalidException,
            PartitionAlreadyExistsException {
        throw new CatalogException("Partitions not supported");
    }

    @Override
    public void dropPartition(
            final ObjectPath path,
            final CatalogPartitionSpec spec,
            final boolean ignoreIfNotExists) throws PartitionNotExistException {
        throw new CatalogException("Partitions not supported");
    }

    @Override
    public void alterPartition(
            final ObjectPath path,
            final CatalogPartitionSpec spec,
            final CatalogPartition newPart,
            final boolean ignoreIfNotExists) throws PartitionNotExistException {
        throw new CatalogException("Partitions not supported");
    }

    @Override
    public CatalogTableStatistics getTableStatistics(final ObjectPath path)
            throws TableNotExistException {
        return CatalogTableStatistics.UNKNOWN;
    }

    @Override
    public CatalogColumnStatistics getTableColumnStatistics(
            final ObjectPath path) throws TableNotExistException {
        return CatalogColumnStatistics.UNKNOWN;
    }

    @Override
    public CatalogTableStatistics getPartitionStatistics(
            final ObjectPath path,
            final CatalogPartitionSpec spec) throws PartitionNotExistException {
        return CatalogTableStatistics.UNKNOWN;
    }

    @Override
    public CatalogColumnStatistics getPartitionColumnStatistics(
            final ObjectPath path,
            final CatalogPartitionSpec spec) throws PartitionNotExistException {
        return CatalogColumnStatistics.UNKNOWN;
    }

    @Override
    public void alterTableStatistics(
            final ObjectPath path,
            final CatalogTableStatistics stats,
            final boolean ignoreIfNotExists) throws TableNotExistException {
        throw new CatalogException("Statistics not supported");
    }

    @Override
    public void alterTableColumnStatistics(
            final ObjectPath path,
            final CatalogColumnStatistics stats,
            final boolean ignoreIfNotExists) throws TableNotExistException,
            TablePartitionedException {
        throw new CatalogException("Statistics not supported");
    }

    @Override
    public void alterPartitionStatistics(
            final ObjectPath path,
            final CatalogPartitionSpec spec,
            final CatalogTableStatistics stats,
            final boolean ignoreIfNotExists) throws PartitionNotExistException {
        throw new CatalogException("Statistics not supported");
    }

    @Override
    public void alterPartitionColumnStatistics(
            final ObjectPath path,
            final CatalogPartitionSpec spec,
            final CatalogColumnStatistics stats,
            final boolean ignoreIfNotExists) throws PartitionNotExistException {
        throw new CatalogException("Statistics not supported");
    }

    private ResolvedSchema convertToFlinkSchema(
            final String content,
            final String artType) {
        if ("AVRO".equalsIgnoreCase(artType)) {
            return AvroFlinkTypeConverter.convert(content);
        } else if ("JSON".equalsIgnoreCase(artType)) {
            return JsonSchemaFlinkTypeConverter.convert(content);
        } else {
            throw new CatalogException("Unsupported type: " + artType);
        }
    }

    private boolean isNotFoundError(final Exception e) {
        final String msg = e.getMessage();
        return msg != null
                && (msg.contains("404") || msg.contains("not found"));
    }

    /**
     * Cache entry for schema data.
     */
    private static final class CachedSchema {

        /** The cached table. */
        private final CatalogBaseTable cachedTable;

        /** The timestamp when cached. */
        private final long cachedAt;

        CachedSchema(final CatalogBaseTable tbl, final long ts) {
            this.cachedTable = tbl;
            this.cachedAt = ts;
        }

        CatalogBaseTable getTable() {
            return cachedTable;
        }

        boolean isExpired(final long ttl) {
            return System.currentTimeMillis() - cachedAt > ttl;
        }
    }

    /**
     * Configuration for ApicurioCatalog.
     */
    public static final class CatalogConfig {

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

        private CatalogConfig(final Builder b) {
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
            private long bCacheTtlMs =
                    ApicurioCatalogOptions.DEFAULT_CACHE_TTL_MS;

            private Builder() {
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
}
