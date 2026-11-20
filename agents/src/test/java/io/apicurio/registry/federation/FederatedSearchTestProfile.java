package io.apicurio.registry.federation;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * Federation and A2A enabled, with peers allowed on plain HTTP at a loopback address so that
 * stub peers and the registry itself can be peers, and with short deadlines.
 */
public class FederatedSearchTestProfile implements QuarkusTestProfile {

    static Map<String, String> overrides() {
        Map<String, String> overrides = new HashMap<>(FederationEnabledProfile.federationOverrides());
        overrides.put("apicurio.a2a.enabled", "true");
        overrides.put("apicurio.federation.peer.insecure-http.enabled", "true");
        overrides.put("apicurio.federation.peer.loopback.enabled", "true");
        overrides.put("apicurio.federation.search.deadline-ms", "2500");
        overrides.put("apicurio.federation.search.peer-timeout-ms", "1000");
        return overrides;
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return overrides();
    }
}
