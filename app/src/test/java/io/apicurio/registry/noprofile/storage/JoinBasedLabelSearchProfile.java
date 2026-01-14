package io.apicurio.registry.noprofile.storage;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * Test profile that enables JOIN-based label filtering for search queries.
 * This tests the optimized search path using INNER/LEFT JOIN instead of EXISTS subqueries.
 */
public class JoinBasedLabelSearchProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put("apicurio.storage.sql.search.use-join-for-labels", "true");
        return props;
    }
}
