package io.apicurio.registry;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.Map;

public class ImportLifecycleBeanTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Collections.singletonMap("apicurio.import.url", getClass().getResource("rest/v3/export.zip").toExternalForm());
    }

}
