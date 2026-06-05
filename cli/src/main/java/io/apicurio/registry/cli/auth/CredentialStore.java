package io.apicurio.registry.cli.auth;

import io.apicurio.registry.cli.common.CliException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;

/**
 * Stores and retrieves credentials per context using the OS keychain.
 */
@ApplicationScoped
public class CredentialStore {

    private static final Logger log = Logger.getLogger(CredentialStore.class);

    @Inject
    CredentialProvider provider;

    public void store(final String contextName, final String key, final String secret) {
        try {
            provider.store(credentialKey(contextName, key), secret);
        } catch (CredentialStoreException ex) {
            throw new CliException("Failed to store credentials. Ensure the system keychain is available.",
                    APPLICATION_ERROR_RETURN_CODE);
        }
    }

    public String retrieve(final String contextName, final String key) {
        try {
            return provider.retrieve(credentialKey(contextName, key));
        } catch (CredentialStoreException ex) {
            log.debugf("Could not retrieve credential (%s).", ex.getClass().getSimpleName());
            return null;
        }
    }

    public void delete(final String contextName, final String key) {
        provider.delete(credentialKey(contextName, key));
    }

    private static String credentialKey(final String contextName, final String key) {
        return contextName + "/" + key;
    }
}
