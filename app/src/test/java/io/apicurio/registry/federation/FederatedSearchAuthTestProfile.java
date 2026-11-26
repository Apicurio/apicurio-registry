package io.apicurio.registry.federation;

import io.apicurio.registry.auth.A2AAuthTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * The A2A authentication profile (basic authentication, role-based authorization, owner-only
 * authorization) with federated search enabled as in {@link FederatedSearchTestProfile}.
 */
public class FederatedSearchAuthTestProfile extends A2AAuthTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> overrides = new HashMap<>(super.getConfigOverrides());
        overrides.putAll(FederatedSearchTestProfile.overrides());
        // Authenticated, but with no role, so not allowed to read.
        overrides.put("quarkus.security.users.embedded.users.carol", "carol");
        return overrides;
    }
}
