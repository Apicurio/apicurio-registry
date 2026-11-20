package io.apicurio.registry.agents.aicatalog;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.config.InstanceConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_AICATALOG;

/**
 * Configuration properties for the AI Catalog (ai-catalog.io) well-known discovery endpoint.
 */
@Singleton
public class AiCatalogConfig {

    /** The AI Catalog specification version the catalog document implements. */
    public static final String SPEC_VERSION = "1.0";

    @ConfigProperty(name = "apicurio.ai-catalog.enabled")
    @Info(category = CATEGORY_AICATALOG, description = "Enable the AI Catalog well-known discovery endpoint. Defaults to apicurio.agents.enabled", dependsOn = "apicurio.agents.enabled", availableSince = "3.3.3", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.ai-catalog.publisher-domain")
    @Info(category = CATEGORY_AICATALOG, description = "The publisher domain segment used when building 'urn:air:' identifiers for AI Catalog entries. When not configured, the domain is derived from the incoming request's host and port.", availableSince = "3.3.3")
    Optional<String> publisherDomain;

    @ConfigProperty(name = "apicurio.ai-catalog.host-name")
    @Info(category = CATEGORY_AICATALOG, description = "Deprecated since 3.4.0, use apicurio.instance.name, which takes precedence. Display name for this registry instance, reported as the AI Catalog host", availableSince = "3.3.3")
    Optional<String> hostName;

    @Inject
    InstanceConfig instance;


    public boolean isEnabled() {
        return enabled;
    }

    public Optional<String> getPublisherDomain() {
        return publisherDomain;
    }

    public String getHostName() {
        return instance.nameOr(hostName);
    }

}
