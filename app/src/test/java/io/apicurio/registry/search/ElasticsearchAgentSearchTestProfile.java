package io.apicurio.registry.search;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class ElasticsearchAgentSearchTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "apicurio.features.experimental.enabled", "true",
                "apicurio.a2a.enabled", "true",
                "apicurio.search.index.enabled", "true",
                "apicurio.rest.deletion.group.enabled", "true",
                "apicurio.rest.deletion.artifact.enabled", "true",
                "apicurio.rest.deletion.artifact-version.enabled", "true",
                "quarkus.elasticsearch.devservices.enabled", "true"
        );
    }
}
