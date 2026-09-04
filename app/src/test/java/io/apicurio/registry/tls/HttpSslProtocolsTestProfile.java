package io.apicurio.registry.tls;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class HttpSslProtocolsTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();

        // Enable HTTPS with self-signed certificate using random available port
        props.put("quarkus.http.ssl-port", "0");
        props.put("quarkus.http.ssl.certificate.key-store-file", "tls/registry-keystore.jks");
        props.put("quarkus.http.ssl.certificate.key-store-password", "registrytest");
        props.put("quarkus.http.insecure-requests", "disabled"); // Force HTTPS only

        // Use our new Apicurio configuration to restrict TLS to TLSv1.3 only
        props.put("apicurio.http.ssl.protocols", "TLSv1.3");

        return props;
    }
}
