package io.apicurio.registry.cli.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.utils.Mapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
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
        if (credentials.isEmpty()) {
            try {
                Files.deleteIfExists(credentialsPath());
            } catch (IOException ex) {
                // Non-critical — empty file is harmless
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
        } catch (UnsupportedOperationException ex) {
            // POSIX permissions are unavailable on Windows; fall back to an owner-only ACL.
            restrictWindowsAcl(path);
        } catch (IOException ex) {
            warnPermissionsNotRestricted();
        }
    }

    private static void restrictWindowsAcl(final Path path) {
        try {
            final AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (view == null) {
                warnPermissionsNotRestricted();
                return;
            }
            final UserPrincipal owner = view.getOwner();
            final AclEntry entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .build();
            // Replace the DACL so that only the owner can read the credentials file.
            //
            // Entries inherited from the containing directory do not survive this, but that is a
            // consequence of how the JDK writes the ACL rather than something it asks for:
            // setAcl calls the legacy SetFileSecurity with DACL_SECURITY_INFORMATION alone, and
            // because the descriptor it builds is not marked auto-inherited, Windows stores the
            // DACL verbatim instead of merging inheritable entries back in. Since the guarantee
            // rests on that detail rather than on an explicit request, it is checked below rather
            // than assumed, and a failure is reported the same way as any other.
            view.setAcl(List.of(entry));
            if (!isRestrictedToOwner(view, owner)) {
                warnPermissionsNotRestricted();
            }
        } catch (IOException | RuntimeException ex) {
            warnPermissionsNotRestricted();
        }
    }

    /**
     * Whether the file's effective ACL grants access to nobody but its owner. The list returned
     * for a Windows file is the whole DACL, so an entry inherited from the parent directory would
     * appear here too.
     */
    private static boolean isRestrictedToOwner(final AclFileAttributeView view,
                                               final UserPrincipal owner) throws IOException {
        final List<AclEntry> acl = view.getAcl();
        return acl.size() == 1 && owner.equals(acl.get(0).principal());
    }

    private static void warnPermissionsNotRestricted() {
        log.warnf("Could not restrict file permissions on %s"
                + " — credentials may be readable by other users.", CREDENTIALS_FILE);
    }

    private Path credentialsPath() {
        return config.getAcrCurrentHomePath().resolve(CREDENTIALS_FILE);
    }
}
