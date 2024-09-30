package io.apicurio.registry.event;

import io.apicurio.registry.utils.tests.DebeziumContainerResource;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class EventsTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Collections.singletonMap("apicurio.storage.sql.kind", "postgresql");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(DebeziumContainerResource.class));
    }
}