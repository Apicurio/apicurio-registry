package io.apicurio.registry.storage.util;

import io.apicurio.registry.utils.tests.MySqlEmbeddedTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MysqlTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Collections.singletonMap("apicurio.storage.sql.kind", "mysql");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            return List.of(new TestResourceEntry(MySqlEmbeddedTestResource.class));
        } else {
            return Collections.emptyList();
        }
    }
}
