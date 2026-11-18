package io.apicurio.registry.agents;

import io.apicurio.registry.utils.tests.MsSqlEmbeddedTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Every agent feature on SQL Server, so the agent test suites that run on this database share one
 * application. The settings are the union of the profiles those suites use on H2.
 */
public class AgentFeaturesMssqlProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.ofEntries(
                Map.entry("apicurio.storage.sql.kind", "mssql"),
                Map.entry("apicurio.features.experimental.enabled", "true"),
                Map.entry("apicurio.a2a.enabled", "true"),
                Map.entry("apicurio.mcp-tools.enabled", "true"),
                Map.entry("apicurio.mcp-registry.enabled", "true"),
                Map.entry("apicurio.mcp-import.enabled", "true"),
                Map.entry("apicurio.mcp-import.allowed-hosts", "127.0.0.1"),
                Map.entry("apicurio.mcp-import.loopback.enabled", "true"),
                Map.entry("apicurio.mcp-import.insecure-http.enabled", "true"),
                Map.entry("apicurio.mcp-import.timeout.seconds", "2"),
                Map.entry("apicurio.rest.mutability.artifact-version-content.enabled", "true"));
    }

    @Override
    public List<TestResourceEntry> testResources() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            return List.of(new TestResourceEntry(MsSqlEmbeddedTestResource.class));
        } else {
            return Collections.emptyList();
        }
    }
}
