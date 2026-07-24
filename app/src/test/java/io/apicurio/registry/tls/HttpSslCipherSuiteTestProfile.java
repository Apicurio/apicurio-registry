package io.apicurio.registry.tls;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class HttpSslCipherSuiteTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();

        // Enable HTTPS with self-signed certificate using random available port
        props.put("quarkus.http.ssl-port", "0");
        props.put("quarkus.http.ssl.certificate.key-store-file", "tls/registry-keystore.jks");
        props.put("quarkus.http.ssl.certificate.key-store-password", "registrytest");
        props.put("quarkus.http.insecure-requests", "disabled"); // Force HTTPS only

        // Restrict to TLSv1.3 and allow only TLS_AES_256_GCM_SHA384
        props.put("apicurio.http.ssl.protocols", "TLSv1.3");
        props.put("apicurio.http.ssl.cipher-suites", "TLS_AES_256_GCM_SHA384");

        return props;
    }
}
