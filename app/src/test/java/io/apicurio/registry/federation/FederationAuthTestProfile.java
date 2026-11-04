package io.apicurio.registry.federation;

import io.apicurio.registry.utils.tests.AuthTestProfile;

import java.util.Map;

/**
 * Keycloak-backed authentication and role-based authorization with federation enabled.
 */
public class FederationAuthTestProfile extends AuthTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = super.getConfigOverrides();
        props.putAll(FederationEnabledProfile.federationOverrides());
        return props;
    }
}
