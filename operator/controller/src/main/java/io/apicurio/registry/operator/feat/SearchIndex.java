package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.spec.SearchIndexSpec;
import io.apicurio.registry.operator.utils.SecretKeyRefTool;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.apicurio.registry.operator.EnvironmentVariables.*;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;

/**
 * Configures Elasticsearch-based search indexing for the Apicurio Registry application.
 * <p>
 * When search indexing is enabled, the operator sets environment variables to configure:
 * <ul>
 *   <li>Experimental feature gate (required for search indexing)</li>
 *   <li>Search index enablement</li>
 *   <li>Elasticsearch connection (hosts, credentials)</li>
 *   <li>Index name (optional)</li>
 * </ul>
 */
public class SearchIndex {

    private static final Logger log = LoggerFactory.getLogger(SearchIndex.class);

    /**
     * Configure Elasticsearch search indexing for the application based on the provided SearchIndexSpec.
     *
     * @param searchIndexSpec the search index specification from the CR
     * @param envVars the environment variables map to populate
     */
    public static void configureSearchIndex(SearchIndexSpec searchIndexSpec, Map<String, EnvVar> envVars) {
        if (searchIndexSpec == null) {
            return;
        }

        Boolean enabled = searchIndexSpec.getEnabled();
        if (enabled == null || !enabled) {
            log.debug("Elasticsearch search indexing is not enabled");
            return;
        }

        log.info("Configuring Elasticsearch search indexing");

        // Enable the experimental feature gate (required for search indexing)
        addEnvVar(envVars, new EnvVarBuilder()
                .withName(APICURIO_FEATURES_EXPERIMENTAL_ENABLED)
                .withValue("true")
                .build());

        // Enable search indexing
        addEnvVar(envVars, new EnvVarBuilder()
                .withName(APICURIO_SEARCH_INDEX_ENABLED)
                .withValue("true")
                .build());

        // Configure Elasticsearch hosts
        String hosts = searchIndexSpec.getHosts();
        if (hosts != null && !hosts.isBlank()) {
            addEnvVar(envVars, new EnvVarBuilder()
                    .withName(QUARKUS_ELASTICSEARCH_HOSTS)
                    .withValue(hosts)
                    .build());
            log.debug("Elasticsearch hosts: {}", hosts);
        }

        // Configure index name (optional)
        String indexName = searchIndexSpec.getIndexName();
        if (indexName != null && !indexName.isBlank()) {
            addEnvVar(envVars, new EnvVarBuilder()
                    .withName(APICURIO_SEARCH_INDEX_ELASTICSEARCH_INDEX_NAME)
                    .withValue(indexName)
                    .build());
            log.debug("Elasticsearch index name: {}", indexName);
        }

        // Configure username (optional)
        String username = searchIndexSpec.getUsername();
        if (username != null && !username.isBlank()) {
            addEnvVar(envVars, new EnvVarBuilder()
                    .withName(QUARKUS_ELASTICSEARCH_USERNAME)
                    .withValue(username)
                    .build());
            log.debug("Elasticsearch username configured");
        }

        // Configure password from secret reference (optional)
        var password = new SecretKeyRefTool(searchIndexSpec.getPassword(), "password");
        if (password.isValid()) {
            password.applySecretEnvVar(envVars, QUARKUS_ELASTICSEARCH_PASSWORD);
            log.debug("Elasticsearch password configured from secret");
        }
    }
}
