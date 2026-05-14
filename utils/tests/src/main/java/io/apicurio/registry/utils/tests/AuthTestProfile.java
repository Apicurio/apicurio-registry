package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuthTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put("apicurio.rest.deletion.group.enabled", "true");
        props.put("apicurio.rest.deletion.artifact.enabled", "true");
        props.put("apicurio.rest.deletion.artifact-version.enabled", "true");
        // Enable proxy-header auth alongside OIDC so dual-mode tests can run under this
        // same profile (avoiding an extra Quarkus augmentation). The proxy-header mechanism
        // returns null when no proxy headers are present, so OIDC-only tests are unaffected.
        props.put("apicurio.authn.proxy-header.enabled", "true");
        props.put("apicurio.authn.mechanism.priority", "proxy-header,oidc");
        return props;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            return List.of(new TestResourceEntry(KeycloakTestContainerManager.class));
        } else {
            return Collections.emptyList();
        }
    }
}
