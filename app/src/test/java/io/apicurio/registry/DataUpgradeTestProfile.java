package io.apicurio.registry;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.Map;

public class DataUpgradeTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Collections.singletonMap("apicurio.upgrade.file.location",
                getClass().getResource("upgrade/v2_export.zip").toExternalForm());
    }

}
