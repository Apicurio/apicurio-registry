package io.apicurio.registry.cli.auth;

import io.apicurio.registry.cli.utils.PlatformUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Selects the platform-specific credential provider at runtime.
 */
@ApplicationScoped
public class CredentialProviderProducer {

    private static final String SERVICE_NAME = "apicurio-registry-cli";

    @Produces
    @ApplicationScoped
    CredentialProvider createProvider() {
        if (PlatformUtils.isMacOS()) {
            return new MacOSCredentialProvider(SERVICE_NAME);
        }
        return new LinuxCredentialProvider(SERVICE_NAME);
    }
}
