package io.apicurio.registry.aicatalog;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_AICATALOG;

/**
 * Configuration properties for the AI Catalog (ai-catalog.io) well-known discovery endpoint.
 */
@Singleton
public class AiCatalogConfig {

    @ConfigProperty(name = "apicurio.ai-catalog.enabled", defaultValue = "false")
    @Info(category = CATEGORY_AICATALOG, description = "Enable the AI Catalog well-known discovery endpoint", availableSince = "3.3.3", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.ai-catalog.publisher-domain")
    @Info(category = CATEGORY_AICATALOG, description = "The publisher domain segment used when building 'urn:air:' identifiers for AI Catalog entries. When not configured, the domain is derived from the incoming request's host and port.", availableSince = "3.3.3")
    Optional<String> publisherDomain;

    @ConfigProperty(name = "apicurio.ai-catalog.host-name", defaultValue = "Apicurio Registry")
    @Info(category = CATEGORY_AICATALOG, description = "Display name for this registry instance, reported as the AI Catalog host", availableSince = "3.3.3")
    String hostName;

    @ConfigProperty(name = "apicurio.ai-catalog.spec-version", defaultValue = "1.0")
    @Info(category = CATEGORY_AICATALOG, description = "The AI Catalog specification version reported in the catalog document", availableSince = "3.3.3")
    String specVersion;

    public boolean isEnabled() {
        return enabled;
    }

    public Optional<String> getPublisherDomain() {
        return publisherDomain;
    }

    public String getHostName() {
        return hostName;
    }

    public String getSpecVersion() {
        return specVersion;
    }
}
