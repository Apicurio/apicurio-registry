package io.apicurio.registry.storage.impl.search;

import io.apicurio.common.apps.config.Info;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_SEARCH;

/**
 * Configuration for Elasticsearch-based content search indexing.
 */
@ApplicationScoped
public class ElasticsearchSearchConfig {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchSearchConfig.class);

    @ConfigProperty(name = "apicurio.search.index.enabled", defaultValue = "false")
    @Info(category = CATEGORY_SEARCH, description = "Enable search indexing", availableSince = "3.2.0")
    boolean enabled;

    @ConfigProperty(name = "apicurio.search.index.elasticsearch.index-name",
            defaultValue = "apicurio-registry")
    @Info(category = CATEGORY_SEARCH, description = "Elasticsearch index name", availableSince = "3.2.0")
    String indexName;

    @ConfigProperty(name = "apicurio.search.index.elasticsearch.number-of-shards", defaultValue = "1")
    @Info(category = CATEGORY_SEARCH, description = "Number of Elasticsearch index shards",
            availableSince = "3.2.0")
    int numberOfShards;

    @ConfigProperty(name = "apicurio.search.index.elasticsearch.number-of-replicas", defaultValue = "1")
    @Info(category = CATEGORY_SEARCH, description = "Number of Elasticsearch index replicas",
            availableSince = "3.2.0")
    int numberOfReplicas;

    @PostConstruct
    void initialize() {
        if (enabled) {
            log.info("Elasticsearch search index ENABLED");
            log.info("  - Index name: {}", indexName);
            log.info("  - Shards: {}, Replicas: {}", numberOfShards, numberOfReplicas);
        } else {
            log.info("Elasticsearch search index DISABLED");
        }
    }

    /**
     * Returns whether Elasticsearch search indexing is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the Elasticsearch index name.
     *
     * @return the index name
     */
    public String getIndexName() {
        return indexName;
    }

    /**
     * Returns the number of shards for the Elasticsearch index.
     *
     * @return the number of shards
     */
    public int getNumberOfShards() {
        return numberOfShards;
    }

    /**
     * Returns the number of replicas for the Elasticsearch index.
     *
     * @return the number of replicas
     */
    public int getNumberOfReplicas() {
        return numberOfReplicas;
    }
}
