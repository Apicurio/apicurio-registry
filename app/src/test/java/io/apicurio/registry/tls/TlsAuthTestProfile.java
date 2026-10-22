package io.apicurio.registry.tls;

import io.apicurio.registry.utils.tests.KeycloakTestContainerManager;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test profile that combines TLS/HTTPS configuration for the Registry server
 * with authentication via Keycloak that is also configured with HTTPS/TLS.
 *
 * This profile is used to verify that TLS configuration in SerDe clients
 * applies to both Registry API calls and Keycloak authentication calls.
 */
public class TlsAuthTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();

        // Enable HTTPS for Registry with self-signed certificate
        props.put("quarkus.http.ssl-port", "0"); // Use random available port
        props.put("quarkus.http.ssl.certificate.key-store-file", "tls/registry-keystore.jks");
        props.put("quarkus.http.ssl.certificate.key-store-password", "registrytest");
        props.put("quarkus.http.insecure-requests", "disabled"); // Force HTTPS only

        // Enable artifact/group deletion for testing
        props.put("apicurio.rest.deletion.group.enabled", "true");
        props.put("apicurio.rest.deletion.artifact.enabled", "true");
        props.put("apicurio.rest.deletion.artifact-version.enabled", "true");

        return props;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        // Start Keycloak with HTTPS enabled by passing tls.enabled=true
        return List.of(new TestResourceEntry(
                KeycloakTestContainerManager.class,
                Map.of("tls.enabled", "true")
        ));
    }
}
