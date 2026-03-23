package io.apicurio.registry.storage.impl.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.GetIndicesSettingsResponse;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Manages the Elasticsearch index lifecycle: creation, mapping, refresh, and document count.
 */
@ApplicationScoped
public class ElasticsearchIndexManager {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexManager.class);

    private static final int CURRENT_MAPPING_VERSION = 1;
    public static final String MAPPING_VERSION_DOC_ID = "_mapping_version";
    public static final String REINDEX_LOCK_DOC_ID = "_reindex_lock";

    /**
     * List of all internal metadata document IDs that should be excluded from user-facing
     * queries and operations.
     */
    public static final List<String> INTERNAL_DOC_IDS = List.of(
            MAPPING_VERSION_DOC_ID, REINDEX_LOCK_DOC_ID);

    @Inject
    ElasticsearchClient client;

    @Inject
    ElasticsearchSearchConfig config;

    /**
     * Ensures the Elasticsearch index exists with the correct mapping. Creates it if it does
     * not exist. If the index exists but has an outdated mapping version, deletes and recreates
     * it so the new mapping takes effect. Safe to call from multiple replicas concurrently — if
     * the index is created by another replica between our existence check and our create call,
     * the {@code resource_already_exists_exception} is caught and treated as success.
     *
     * @throws IOException if an error occurs communicating with Elasticsearch
     */
    public void ensureIndexExists() throws IOException {
        String indexName = config.getIndexName();

        boolean exists = client.indices().exists(e -> e.index(indexName)).value();
        if (exists) {
            int mappingVersion = readMappingVersion();
            if (mappingVersion >= CURRENT_MAPPING_VERSION) {
                log.info("Elasticsearch index '{}' already exists with mapping version {}.",
                        indexName, mappingVersion);
                return;
            }

            log.warn("Elasticsearch index '{}' has outdated mapping version {} (current: {}). "
                    + "Deleting and recreating the index.", indexName, mappingVersion,
                    CURRENT_MAPPING_VERSION);
            deleteIndex();
        }

        log.info("Creating Elasticsearch index '{}'...", indexName);

        try {
            CreateIndexResponse response = client.indices().create(c -> c
                    .index(indexName)
                    .settings(buildSettings())
                    .mappings(buildMapping())
            );

            if (response.acknowledged()) {
                log.info("Elasticsearch index '{}' created successfully.", indexName);
            } else {
                log.warn("Elasticsearch index '{}' creation was not acknowledged.", indexName);
            }

            writeMappingVersion();
        } catch (ElasticsearchException e) {
            // Handle race condition: another replica created the index between our
            // exists check and our create call.
            if ("resource_already_exists_exception".equals(e.error().type())) {
                log.info("Elasticsearch index '{}' was created by another replica.", indexName);
            } else {
                throw e;
            }
        }
    }

    /**
     * Deletes all content documents from the index, preserving internal metadata documents
     * such as the mapping version tracker. Used for full reindex scenarios.
     *
     * @throws IOException if an error occurs communicating with Elasticsearch
     */
    public void deleteAllDocuments() throws IOException {
        String indexName = config.getIndexName();
        client.deleteByQuery(d -> d
                .index(indexName)
                .query(q -> q.bool(b -> b
                        .mustNot(mn -> mn.ids(ids -> ids.values(INTERNAL_DOC_IDS)))
                ))
        );
        log.info("Deleted all content documents from index '{}'.", indexName);
    }

    /**
     * Explicitly refreshes the index to make recently indexed documents searchable.
     *
     * @throws IOException if an error occurs communicating with Elasticsearch
     */
    public void refresh() throws IOException {
        client.indices().refresh(r -> r.index(config.getIndexName()));
    }

    /**
     * Reads the current refresh interval setting from the Elasticsearch index.
     *
     * @return the current refresh interval value (e.g. "1s"), or null if not explicitly set
     * @throws IOException if an error occurs communicating with Elasticsearch
     */
    public String getRefreshInterval() throws IOException {
        GetIndicesSettingsResponse response = client.indices().getSettings(
                s -> s.index(config.getIndexName()));
        IndexSettings settings = response.get(config.getIndexName()).settings();
        if (settings != null && settings.index() != null
                && settings.index().refreshInterval() != null) {
            return settings.index().refreshInterval().time();
        }
        return null;
    }

    /**
     * Disables automatic index refreshes by setting the refresh interval to -1. This
     * improves bulk indexing throughput by preventing unnecessary segment rebuilds during
     * large batch operations.
     *
     * @throws IOException if an error occurs communicating with Elasticsearch
     */
    public void disableRefresh() throws IOException {
        client.indices().putSettings(s -> s
                .index(config.getIndexName())
                .settings(is -> is.refreshInterval(t -> t.time("-1")))
        );
        log.info("Disabled automatic refresh for index '{}'.", config.getIndexName());
    }

    /**
     * Restores the automatic refresh interval to the specified value.
     *
     * @param interval the refresh interval to restore (e.g. "1s"), or null to restore the
     *                 Elasticsearch default
     * @throws IOException if an error occurs communicating with Elasticsearch
     */
    public void restoreRefresh(String interval) throws IOException {
        String restoreValue = interval != null ? interval : "1s";
        client.indices().putSettings(s -> s
                .index(config.getIndexName())
                .settings(is -> is.refreshInterval(t -> t.time(restoreValue)))
        );
        log.info("Restored refresh interval to '{}' for index '{}'.", restoreValue,
                config.getIndexName());
    }

    /**
     * Returns the number of content documents in the index, excluding internal metadata
     * documents such as the mapping version tracker.
     *
     * @return the document count
     * @throws IOException if an error occurs communicating with Elasticsearch
     */
    public long count() throws IOException {
        CountResponse response = client.count(c -> c
                .index(config.getIndexName())
                .query(q -> q.bool(b -> b
                        .mustNot(mn -> mn.ids(ids -> ids.values(INTERNAL_DOC_IDS)))
                ))
        );
        return response.count();
    }

    /**
     * Reads the mapping version from the metadata document stored in the index.
     *
     * @return the mapping version, or {@code 0} if the document does not exist (pre-versioning index)
     * @throws IOException if an error occurs communicating with Elasticsearch
     */
    @SuppressWarnings("rawtypes")
    private int readMappingVersion() throws IOException {
        String indexName = config.getIndexName();
        try {
            GetResponse<Map> response = client.get(g -> g
                    .index(indexName)
                    .id(MAPPING_VERSION_DOC_ID), Map.class);
            if (response.found() && response.source() != null) {
                Object version = response.source().get("version");
                if (version instanceof Number) {
                    return ((Number) version).intValue();
                }
            }
        } catch (ElasticsearchException e) {
            log.warn("Failed to read mapping version from index '{}': {}", indexName, e.getMessage());
        }
        return 0;
    }

    /**
     * Writes the current mapping version as a metadata document in the index.
     *
     * @throws IOException if an error occurs communicating with Elasticsearch
     */
    private void writeMappingVersion() throws IOException {
        String indexName = config.getIndexName();
        client.index(i -> i
                .index(indexName)
                .id(MAPPING_VERSION_DOC_ID)
                .document(Map.of("type", "mapping_version", "version", CURRENT_MAPPING_VERSION))
        );
        log.info("Wrote mapping version {} to index '{}'.", CURRENT_MAPPING_VERSION, indexName);
    }

    /**
     * Deletes the entire Elasticsearch index. Used when the mapping version is outdated
     * and a full recreation with the new mapping is needed.
     *
     * @throws IOException if an error occurs communicating with Elasticsearch
     */
    private void deleteIndex() throws IOException {
        String indexName = config.getIndexName();
        client.indices().delete(d -> d.index(indexName));
        log.info("Deleted Elasticsearch index '{}'.", indexName);
    }

    /**
     * Builds the Elasticsearch index settings.
     *
     * @return the index settings
     */
    private IndexSettings buildSettings() {
        return IndexSettings.of(s -> s
                .numberOfShards(String.valueOf(config.getNumberOfShards()))
                .numberOfReplicas(String.valueOf(config.getNumberOfReplicas()))
        );
    }

    /**
     * Builds the Elasticsearch index mapping with all fields.
     *
     * @return the type mapping
     */
    private TypeMapping buildMapping() {
        return TypeMapping.of(m -> m
                .properties("globalId", Property.of(p -> p.long_(l -> l)))
                .properties("contentId", Property.of(p -> p.long_(l -> l)))
                .properties("groupId", Property.of(p -> p.keyword(k -> k)))
                .properties("artifactId", Property.of(p -> p.keyword(k -> k)))
                .properties("version", Property.of(p -> p.keyword(k -> k)))
                .properties("artifactType", Property.of(p -> p.keyword(k -> k)))
                .properties("state", Property.of(p -> p.keyword(k -> k)))
                .properties("name", Property.of(p -> p.text(t -> t
                        .fields("keyword", Property.of(f -> f.keyword(k -> k)))
                )))
                .properties("description", Property.of(p -> p.text(t -> t)))
                .properties("content", Property.of(p -> p.text(t -> t)))
                .properties("owner", Property.of(p -> p.text(t -> t)))
                .properties("modifiedBy", Property.of(p -> p.keyword(k -> k.index(false))))
                .properties("createdOn", Property.of(p -> p.long_(l -> l)))
                .properties("modifiedOn", Property.of(p -> p.long_(l -> l)))
                .properties("versionOrder", Property.of(p -> p.integer(i -> i)))
                .properties("labels", Property.of(p -> p.nested(n -> n
                        .properties("key", Property.of(lp -> lp.text(t -> t
                                .fields("keyword", Property.of(f -> f.keyword(k -> k)))
                        )))
                        .properties("value", Property.of(lp -> lp.text(t -> t
                                .fields("keyword", Property.of(f -> f.keyword(k -> k)))
                        )))
                )))
                .properties("structure", Property.of(p -> p.keyword(k -> k)))
                .properties("structure_text", Property.of(p -> p.text(t -> t)))
                .properties("structure_kind", Property.of(p -> p.keyword(k -> k)))
                .properties("indexedAt", Property.of(p -> p.long_(l -> l)))
        );
    }
}
