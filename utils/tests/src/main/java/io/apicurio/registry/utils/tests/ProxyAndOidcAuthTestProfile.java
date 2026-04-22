package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test profile that enables both proxy-header and OIDC authentication simultaneously. Proxy
 * headers are tried first; if absent, the request falls back to OIDC authentication via
 * Keycloak.
 */
public class ProxyAndOidcAuthTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();

        props.put("apicurio.rest.deletion.group.enabled", "true");
        props.put("apicurio.rest.deletion.artifact.enabled", "true");
        props.put("apicurio.rest.deletion.artifact-version.enabled", "true");

        // Enable proxy-header authentication (OIDC is enabled by KeycloakTestContainerManager)
        props.put("apicurio.authn.proxy-header.enabled", "true");

        // Chain order: proxy-header first, OIDC fallback
        props.put("apicurio.authn.mechanism.priority", "proxy-header,oidc");

        props.put("apicurio.auth.role-based-authorization", "true");
        props.put("apicurio.auth.admin-override.enabled", "true");

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
