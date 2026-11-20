package io.apicurio.registry.agents.rest.wellknown;

import io.apicurio.registry.agents.aicatalog.AiCatalogConfig;
import io.apicurio.registry.utils.StringUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Resolves request-derived values (external base URL, publisher domain) for discovery responses.
 */
@ApplicationScoped
public class WellKnownRequestSupport {

    @Inject
    AiCatalogConfig aiCatalogConfig;

    // The request-scoped HttpServletRequest bean (quarkus-undertow). @Context only works on JAX-RS
    // resources and providers, not on CDI beans like this one.
    @Inject
    HttpServletRequest request;

    /**
     * Resolves the {@code <publisher>} domain segment used in {@code urn:air:} identifiers
     * and as the AI Catalog host identifier. Uses the configured
     * {@code apicurio.ai-catalog.publisher-domain} when present; otherwise derives it from
     * the incoming request's host and port (the port is always included, per the AI Catalog
     * convention of identifying a specific registry deployment rather than just a hostname).
     */
    String resolvePublisherDomain() {
        Optional<String> configured = aiCatalogConfig.getPublisherDomain();
        if (configured.isPresent() && !StringUtil.isEmpty(configured.get())) {
            return configured.get();
        }

        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (!StringUtil.isEmpty(forwardedHost)) {
            return forwardedHost;
        }

        String host = request.getServerName();
        int port = request.getServerPort();
        return port > 0 ? host + ":" + port : host;
    }

    String getBaseUrl() {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();

        // Check for X-Forwarded headers (common in load balancers/proxies)
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String forwardedHost = request.getHeader("X-Forwarded-Host");

        if (!StringUtil.isEmpty(forwardedProto)) {
            scheme = forwardedProto;
        }
        if (!StringUtil.isEmpty(forwardedHost)) {
            host = forwardedHost;
            port = -1; // Assume standard port when using forwarded host
        }

        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(host);

        if (port > 0 && port != 80 && port != 443) {
            url.append(":").append(port);
        }

        return url.toString();
    }
}
