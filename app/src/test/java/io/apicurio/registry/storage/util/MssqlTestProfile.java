package io.apicurio.registry.storage.util;

import io.apicurio.registry.utils.tests.MsSqlEmbeddedTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MssqlTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Collections.singletonMap("registry.storage.db-kind", "mssql");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            return List.of(
                    new TestResourceEntry(MsSqlEmbeddedTestResource.class));
        } else {
            return Collections.emptyList();
        }
    }

}
