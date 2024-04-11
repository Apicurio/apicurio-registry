package io.apicurio.registry.storage.util;

import io.apicurio.registry.utils.tests.PostgreSqlEmbeddedTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PostgresqlTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Collections.singletonMap("apicurio.storage.db-kind", "postgresql");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            return List.of(
                    new TestResourceEntry(PostgreSqlEmbeddedTestResource.class));
        } else {
            return Collections.emptyList();
        }
    }
}
