package io.apicurio.registry.tls;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class TlsTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();

        // Enable HTTPS with self-signed certificate
        props.put("quarkus.http.ssl-port", "0"); // Use random available port
        props.put("quarkus.http.ssl.certificate.key-store-file", "tls/registry-keystore.jks");
        props.put("quarkus.http.ssl.certificate.key-store-password", "registrytest");
        props.put("quarkus.http.insecure-requests", "disabled"); // Force HTTPS only

        return props;
    }
}