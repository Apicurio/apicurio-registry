package io.apicurio.registry.cli;

import io.apicurio.registry.cli.auth.CredentialProvider;
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

    @Override
    public void store(final String account, final String secret) {
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
