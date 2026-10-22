package io.apicurio.registry.tls;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * Test profile for mutual TLS (mTLS) testing.
 * Configures the server to require client certificates.
 */
public class MtlsTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();

        // Enable HTTPS with self-signed certificate
        props.put("quarkus.http.ssl-port", "0"); // Use random available port

        // Server keystore configuration
        props.put("quarkus.http.ssl.certificate.key-store-file", "tls/registry-keystore.jks");
        props.put("quarkus.http.ssl.certificate.key-store-password", "registrytest");

        // Server truststore configuration (to validate client certificates)
        props.put("quarkus.http.ssl.certificate.trust-store-file", "tls/registry-truststore.jks");
        props.put("quarkus.http.ssl.certificate.trust-store-password", "registrytest");

        // Require client certificates (mutual TLS)
        props.put("quarkus.http.ssl.client-auth", "required");

        // Force HTTPS only
        props.put("quarkus.http.insecure-requests", "disabled");

        // Disable OIDC authentication for this test (we're only testing mTLS, not auth)
        props.put("quarkus.oidc.tenant-enabled", "false");

        // Enable basic auth to satisfy AppAuthenticationMechanism dependencies
        // (even though we won't use it for authentication in this test)
        props.put("quarkus.http.auth.basic", "true");

        return props;
    }
}
