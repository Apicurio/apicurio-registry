package io.apicurio.registry.cli.auth;

import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.config.Config;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;

/**
 * Stores and retrieves credentials per context using the OS keychain or file-based fallback.
 */
@ApplicationScoped
public class CredentialStore {

    private static final Logger log = Logger.getLogger(CredentialStore.class);

    @Inject
    CredentialProvider provider;

    @Inject
    Config config;

    private FileCredentialProvider fileProvider;

    public void store(final String contextName, final String key, final String secret,
                      final boolean allowUnsafe) {
        try {
            provider.store(credentialKey(contextName, key), secret);
        } catch (CredentialStoreException ex) {
            if (allowUnsafe) {
                log.debug("OS keychain not available, using file-based credential storage.");
                getFileProvider().store(credentialKey(contextName, key), secret);
            } else {
                throw new CliException(
                        "Failed to store credentials. Ensure the system keychain is available, "
                                + "or use --allow-unsafe-credential-storage to store credentials in a file.",
                        APPLICATION_ERROR_RETURN_CODE);
            }
        }
    }

    public void store(final String contextName, final String key, final String secret) {
        store(contextName, key, secret, false);
    }

    public String retrieve(final String contextName, final String key,
                           final boolean unsafeStorage) {
        if (unsafeStorage) {
            return getFileProvider().retrieve(credentialKey(contextName, key));
        }
        try {
            return provider.retrieve(credentialKey(contextName, key));
        } catch (CredentialStoreException ex) {
            log.debugf("Could not retrieve credential (%s).", ex.getClass().getSimpleName());
            return null;
        }
    }

    public String retrieve(final String contextName, final String key) {
        return retrieve(contextName, key, false);
    }

    public void delete(final String contextName, final String key) {
        try {
            provider.delete(credentialKey(contextName, key));
        } catch (CredentialStoreException ex) {
            // Ignore — credential may not exist in OS keychain
        }
        getFileProvider().delete(credentialKey(contextName, key));
    }

    private FileCredentialProvider getFileProvider() {
        if (fileProvider == null) {
            fileProvider = new FileCredentialProvider(config);
        }
        return fileProvider;
    }

    private static String credentialKey(final String contextName, final String key) {
        return contextName + "/" + key;
    }
}
