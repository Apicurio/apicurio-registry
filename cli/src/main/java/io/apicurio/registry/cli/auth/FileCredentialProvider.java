package io.apicurio.registry.cli.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.utils.Mapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.jboss.logging.Logger;

/**
 * File-based credential provider for environments without an OS keychain.
 * Stores credentials, encrypted at rest, in a separate JSON file alongside config.json.
 */
class FileCredentialProvider implements CredentialProvider {

    private static final Logger log = Logger.getLogger(FileCredentialProvider.class);

    private static final String CREDENTIALS_FILE = "credentials.json";
    private static final String KEY_FILE = "credentials.key";

    private final Config config;
    private CredentialEncryption encryption;

    FileCredentialProvider(final Config config) {
        this.config = config;
    }

    @Override
    public void store(final String account, final String secret) {
        final var credentials = readCredentials();
        credentials.put(account, encryption().encrypt(secret));
        writeCredentials(credentials);
    }

    @Override
    public String retrieve(final String account) {
        final var credentials = readCredentials();
        final var stored = credentials.get(account);
        if (stored == null) {
            return null;
        }
        if (CredentialEncryption.isEncrypted(stored)) {
            try {
                return encryption().decrypt(stored);
            } catch (CredentialStoreException ex) {
                log.warnf("Could not decrypt stored credential for '%s' (%s)."
                        + " The stored value may be corrupted — please log in again.",
                        account, ex.getMessage());
                return null;
            }
        }
        // Legacy plain text entry — migrate it to encrypted storage before returning it.
        // Migration is best-effort: if the config directory can't be written to (read-only
        // filesystem, full disk, permission regression), the caller still gets a valid
        // credential and migration is retried on the next retrieve() call.
        try {
            credentials.put(account, encryption().encrypt(stored));
            writeCredentials(credentials);
        } catch (CredentialStoreException ex) {
            log.warnf("Could not migrate legacy plain text credential for '%s' to encrypted"
                    + " storage (%s). Will retry on next use.", account, ex.getMessage());
        }
        return stored;
    }

    @Override
    public void delete(final String account) {
        final var credentials = readCredentials();
        credentials.remove(account);
        if (credentials.isEmpty()) {
            try {
                Files.deleteIfExists(credentialsPath());
                Files.deleteIfExists(keyPath());
                // Drop the cached key so a subsequent store() in this same process
                // regenerates it on disk instead of encrypting with an in-memory key
                // that no longer has a backing file.
                resetEncryption();
            } catch (IOException ex) {
                // Non-critical — leftover files are harmless
            }
        } else {
            writeCredentials(credentials);
        }
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
        final Path path = credentialsPath();
        Path temp = null;
        try {
            temp = Files.createTempFile(path.getParent(), "credentials", ".tmp");
            Mapper.MAPPER.writeValue(temp.toFile(), credentials);
            restrictFilePermissions(temp);
            moveFile(temp, path);
        } catch (IOException ex) {
            cleanupTempFile(temp);
            throw new CredentialStoreException("Failed to write credentials file: " + ex.getMessage(), ex);
        }
    }

    private static void moveFile(final Path source, final Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void cleanupTempFile(final Path temp) {
        if (temp != null) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // Non-critical — OS will clean up temp files
            }
        }
    }

    private static void restrictFilePermissions(final Path path) {
        try {
            Files.setPosixFilePermissions(path, EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ex) {
            log.warnf("Could not restrict file permissions on %s"
                    + " — credentials may be readable by other users.", CREDENTIALS_FILE);
        }
    }

    private Path credentialsPath() {
        return config.getAcrCurrentHomePath().resolve(CREDENTIALS_FILE);
    }

    private Path keyPath() {
        return config.getAcrCurrentHomePath().resolve(KEY_FILE);
    }

    private synchronized CredentialEncryption encryption() {
        if (encryption == null) {
            encryption = new CredentialEncryption(keyPath());
        }
        return encryption;
    }

    private synchronized void resetEncryption() {
        encryption = null;
    }
}
