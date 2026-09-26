package io.apicurio.registry.noprofile.rest.a2a;

import io.apicurio.registry.utils.tests.PostgreSqlEmbeddedTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Same overrides as {@link ExperimentalFeaturesEnabledProfile}, but backed by PostgreSQL instead of H2.
 * Used to confirm that the structured content DDL, the EXISTS/NOT EXISTS structure filter and its escaped
 * LIKE pattern behave identically on a second SQL dialect.
 */
public class ExperimentalFeaturesPostgresqlProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "apicurio.features.experimental.enabled", "true",
                "apicurio.a2a.enabled", "true",
                "apicurio.mcp-tools.enabled", "true",
                "apicurio.rest.mutability.artifact-version-content.enabled", "true",
                "apicurio.storage.sql.kind", "postgresql"
        );
    }

    @Override
    public List<TestResourceEntry> testResources() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            return List.of(new TestResourceEntry(PostgreSqlEmbeddedTestResource.class));
        } else {
            return Collections.emptyList();
        }
    }
}
