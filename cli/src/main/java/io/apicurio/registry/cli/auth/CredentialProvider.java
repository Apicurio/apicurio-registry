package io.apicurio.registry.cli.auth;

/**
 * Platform-specific credential storage provider (macOS Keychain, Linux Secret Service).
 */
public interface CredentialProvider {

    void store(String account, String secret);

    String retrieve(String account);

    void delete(String account);
}
