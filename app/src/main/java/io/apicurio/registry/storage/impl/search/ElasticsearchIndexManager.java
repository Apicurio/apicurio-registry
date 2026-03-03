package io.apicurio.registry.storage.impl.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Manages the Elasticsearch index lifecycle: creation, mapping, refresh, and document count.
 */
@ApplicationScoped
public class ElasticsearchIndexManager {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexManager.class);

    @Inject
    ElasticsearchClient client;

    @Inject
    ElasticsearchSearchConfig config;

    /**
     * Ensures the Elasticsearch index exists with the correct mapping. Creates it if it does
     * not exist. Safe to call from multiple replicas concurrently — if the index is created
     * by another replica between our existence check and our create call, the
     * {@code resource_already_exists_exception} is caught and treated as success.
     *
     * @throws IOException if an error occurs communicating with Elasticsearch
     */
    public void ensureIndexExists() throws IOException {
        String indexName = config.getIndexName();

        boolean exists = client.indices().exists(e -> e.index(indexName)).value();
        if (exists) {
            log.info("Elasticsearch index '{}' already exists.", indexName);
            return;
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
     * Deletes all documents from the index. Used for full reindex scenarios.
     *
     * @throws IOException if an error occurs communicating with Elasticsearch
     */
    public void deleteAllDocuments() throws IOException {
        String indexName = config.getIndexName();
        client.deleteByQuery(d -> d
                .index(indexName)
                .query(q -> q.matchAll(m -> m))
        );
        log.info("Deleted all documents from index '{}'.", indexName);
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
     * Returns the number of documents in the index.
     *
     * @return the document count
     * @throws IOException if an error occurs communicating with Elasticsearch
     */
    public long count() throws IOException {
        CountResponse response = client.count(c -> c.index(config.getIndexName()));
        return response.count();
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
