package io.apicurio.registry.agents.mcpimport;

import io.quarkus.test.junit.QuarkusTestProfile;
import io.vertx.core.net.SelfSignedCertificate;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP import trusting a self-signed certificate for localhost through a named TLS configuration
 * of the Quarkus TLS registry, which the JVM default truststore does not trust.
 */
public class McpImportTlsTestProfile implements QuarkusTestProfile {

    static final String TLS_NAME = "mcp-import-test";

    /** Created once per JVM; the test server presents it and the TLS configuration trusts it. */
    static final SelfSignedCertificate CERTIFICATE = SelfSignedCertificate.create("localhost");

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> overrides = new HashMap<>(McpImportTestProfile.importOverrides());
        overrides.put("quarkus.tls." + TLS_NAME + ".trust-store.pem.certs", CERTIFICATE.certificatePath());
        overrides.put("apicurio.mcp-import.tls-configuration.name", TLS_NAME);
        return overrides;
    }
}
