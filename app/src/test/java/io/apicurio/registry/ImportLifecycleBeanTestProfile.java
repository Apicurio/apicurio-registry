package io.apicurio.registry;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.Map;

public class ImportLifecycleBeanTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Collections.singletonMap("registry.import","src/test/resources-unfiltered/io/apicurio/registry/rest/v2/export.zip");
    }

}
