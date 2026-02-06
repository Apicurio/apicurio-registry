package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * Test profile that enables draft production mode. When enabled, draft versions:
 * - Use real content hashes (not draft: prefix)
 * - Are accessible via content lookups (contentId, globalId, contentHash)
 * - Have rules evaluated during creation
 */
public class DraftProductionModeProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put("apicurio.rest.draft.production-mode.enabled", "true");
        props.put("apicurio.rest.mutability.artifact-version-content.enabled", "true");
        return props;
    }
}
