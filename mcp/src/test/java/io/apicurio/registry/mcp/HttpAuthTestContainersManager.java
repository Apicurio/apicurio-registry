package io.apicurio.registry.mcp;

import java.util.HashMap;
import java.util.Map;

/**
 * Test container manager for MCP HTTP transport with inbound OIDC and token forwarding.
 */
public class HttpAuthTestContainersManager extends AuthTestContainersManager {

    @Override
    protected Map<String, String> buildMcpProperties(String registryUrl, String tokenEndpoint,
            String keycloakExternalRealmUrl) {
        Map<String, String> props = new HashMap<>();
        props.put("registry.url", registryUrl);
        props.put("apicurio.mcp.http.enabled", "true");
        props.put("apicurio.mcp.http.forward-token", "true");
        props.put("apicurio.mcp.auth.enabled", "false");
        props.put("quarkus.mcp.server.http.enabled", "true");
        props.put("quarkus.mcp.server.stdio.enabled", "false");
        props.put("quarkus.oidc.tenant-enabled", "true");
        props.put("quarkus.oidc.application-type", "service");
        props.put("quarkus.oidc.auth-server-url", keycloakExternalRealmUrl);
        props.put("quarkus.oidc.tls.verification", "none");
        props.put("quarkus.http.auth.permission.authenticated.paths", "/mcp");
        props.put("quarkus.http.auth.permission.authenticated.policy", "authenticated");
        props.put("quarkus.oidc.tenant-paths", "/mcp");
        props.put("quarkus.http.auth.proactive", "true");
        return props;
    }
}
