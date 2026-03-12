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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flink Catalog implementation backed by Apicurio Registry.
 *
 * <p>
 * Maps Apicurio groups to Flink databases and artifacts to tables.
 */
public final class ApicurioCatalog extends AbstractCatalog {

    /** Logger for this class. */
    private static final Logger LOG = LoggerFactory.getLogger(ApicurioCatalog.class);

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
        this.schemaCache = createBoundedCache(catalogConfig.getCacheMaxSize());
    }

    /**
     * Creates a bounded LRU cache with the specified max size.
     *
     * @param maxSize maximum number of entries before eviction
     * @param <K>     the key type
     * @param <V>     the value type
     * @return a synchronized bounded cache
     */
    private static <K, V> Map<K, V> createBoundedCache(final int maxSize) {
        return Collections.synchronizedMap(
                new LinkedHashMap<K, V>(maxSize, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(
                            final Map.Entry<K, V> eldest) {
                        return size() > maxSize;
                    }
                });
    }

    @Override
    public void open() throws CatalogException {
        LOG.info("Opening catalog: {} at {}", getName(), config.getUrl());
        RegistryClientOptions opts = RegistryClientOptions.create(config.getUrl());
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

            final String content;
            try (InputStream stream = client.groups()
                    .byGroupId(dbName)
                    .artifacts()
                    .byArtifactId(tblName)
                    .versions()
                    .byVersionExpression("branch=latest")
                    .content()
                    .get()) {
                content = new String(
                        stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            final String artType = meta.getArtifactType();
            final ResolvedSchema resolved = convertToFlinkSchema(content, artType);

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
}
