package io.apicurio.registry.search;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that enables Elasticsearch search indexing. Used by integration tests that
 * verify version searches are routed through the Elasticsearch index instead of SQL.
 *
 * <p>Dev Services is disabled by default in {@code application.properties} so that non-search
 * tests don't pay the cost of starting an Elasticsearch container (~15-30s). This profile
 * re-enables it. When Dev Services is enabled and no explicit {@code quarkus.elasticsearch.hosts}
 * is set, Quarkus automatically starts an Elasticsearch container via Testcontainers, injects
 * the connection URL, and provides the {@code ElasticsearchClient} CDI bean. The container
 * lifecycle (start, stop, port mapping) is fully managed by Quarkus.</p>
 */
public class ElasticsearchSearchTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "apicurio.features.experimental.enabled", "true",
                "apicurio.search.index.enabled", "true",
                "apicurio.rest.deletion.group.enabled", "true",
                "apicurio.rest.deletion.artifact.enabled", "true",
                "apicurio.rest.deletion.artifact-version.enabled", "true",
                "quarkus.elasticsearch.devservices.enabled", "true"
        );
    }
}
