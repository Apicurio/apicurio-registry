package io.apicurio.registry.ui;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_UI;

/**
 * Configuration properties for API documentation pages.
 */
@Singleton
public class ApiDocsConfigProperties {

    /**
     * Context path for the application. Used to prefix all absolute paths in API documentation
     * pages when deployed behind a reverse proxy.
     */
    @ConfigProperty(name = "apicurio.app.context-path", defaultValue = "/")
    @Info(category = CATEGORY_UI, description = "Context path for application (useful when behind a proxy)", availableSince = "3.1.0")
    public String contextPath;

}