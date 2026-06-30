package io.apicurio.registry.cli;

import io.apicurio.registry.cli.auth.CredentialProvider;
import io.apicurio.registry.cli.auth.CredentialStoreException;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Test credential provider using in-memory storage. Replaces the OS keychain
 * provider so tests work in CI environments without secret-tool or macOS Keychain.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class TestCredentialProvider implements CredentialProvider {

    private final ConcurrentMap<String, String> store = new ConcurrentHashMap<>();
    private boolean failOnStore;

    public void setFailOnStore(final boolean fail) {
        this.failOnStore = fail;
    }

    @Override
    public void store(final String account, final String secret) {
        if (failOnStore) {
            throw new CredentialStoreException("Simulated keychain failure");
        }
        store.put(account, secret);
    }

    @Override
    public String retrieve(final String account) {
        return store.get(account);
    }

    @Override
    public void delete(final String account) {
        store.remove(account);
    }
}
