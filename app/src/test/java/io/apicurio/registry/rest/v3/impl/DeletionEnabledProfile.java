package io.apicurio.registry.rest.v3.impl;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Quarkus test profile that enables artifact deletion for the REST layer.
 * This profile overrides apicurio.rest.deletion.artifact.enabled to true, so tests such as  GroupsResourceImplTest can remove the
 * artifacts they create and stay independent of each other.
 */
public class DeletionEnabledProfile  implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides(){
        return Map.of("apicurio.rest.deletion.artifact.enabled", "true");
    }
}
