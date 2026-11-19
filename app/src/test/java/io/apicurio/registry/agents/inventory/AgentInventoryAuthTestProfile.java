package io.apicurio.registry.agents.inventory;

import io.apicurio.registry.utils.tests.AuthTestProfile;

import java.util.Map;

/**
 * Keycloak-backed authentication and role-based authorization, with inventory sync enabled as in
 * {@link AgentInventoryTestProfile}.
 */
public class AgentInventoryAuthTestProfile extends AuthTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = super.getConfigOverrides();
        props.putAll(AgentInventoryTestProfile.inventoryOverrides());
        return props;
    }
}
