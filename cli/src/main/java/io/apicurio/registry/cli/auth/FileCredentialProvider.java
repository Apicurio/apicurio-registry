package io.apicurio.registry.cli.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.utils.Mapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.jboss.logging.Logger;

/**
 * File-based credential provider for environments without an OS keychain.
 * Stores credentials in a separate JSON file alongside config.json.
 */
class FileCredentialProvider implements CredentialProvider {

    private static final Logger log = Logger.getLogger(FileCredentialProvider.class);

    private static final String CREDENTIALS_FILE = "credentials.json";

    private final Config config;

    FileCredentialProvider(final Config config) {
        this.config = config;
    }

    @Override
    public void store(final String account, final String secret) {
        final var credentials = readCredentials();
        credentials.put(account, secret);
        writeCredentials(credentials);
    }

    @Override
    public String retrieve(final String account) {
        return readCredentials().get(account);
    }

    @Override
    public void delete(final String account) {
        final var credentials = readCredentials();
        credentials.remove(account);
        writeCredentials(credentials);
    }

    private Map<String, String> readCredentials() {
        final Path path = credentialsPath();
        if (!Files.exists(path)) {
            return new HashMap<>();
        }
        try {
            return Mapper.MAPPER.readValue(path.toFile(), new TypeReference<>() {});
        } catch (IOException ex) {
            log.debugf("Could not read credentials file (%s).", ex.getClass().getSimpleName());
            return new HashMap<>();
        }
    }

    private void writeCredentials(final Map<String, String> credentials) {
        try {
            final Path path = credentialsPath();
            Mapper.MAPPER.writeValue(path.toFile(), credentials);
            try {
                Files.setPosixFilePermissions(path, EnumSet.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ex) {
                // Windows — POSIX permissions not supported
            }
        } catch (IOException ex) {
            throw new CredentialStoreException("Failed to write credentials file: " + ex.getMessage(), ex);
        }
    }

    private Path credentialsPath() {
        return config.getAcrCurrentHomePath().resolve(CREDENTIALS_FILE);
    }
}
