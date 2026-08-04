package io.apicurio.registry.cli.auth;

import io.apicurio.registry.cli.config.Config;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies that credentials written by the file fallback are locked down to the owner on Windows,
 * where POSIX permissions are not available.
 */
@EnabledOnOs(OS.WINDOWS)
public class FileCredentialProviderAclTest {

    @TempDir
    Path home;

    @Test
    public void testCredentialsFileIsRestrictedToOwner() throws IOException {
        final Config config = new Config();
        config.setAcrCurrentHomePath(home);

        final FileCredentialProvider provider = new FileCredentialProvider(config);
        provider.store("dev/password", "s3cr3t");

        final Path credentialsFile = home.resolve("credentials.json");
        assertThat(credentialsFile).exists();

        final AclFileAttributeView view =
                Files.getFileAttributeView(credentialsFile, AclFileAttributeView.class);
        assertThat(view).as("Windows filesystems expose an ACL view").isNotNull();

        final List<AclEntry> acl = view.getAcl();
        assertThat(acl).hasSize(1);

        final AclEntry entry = acl.get(0);
        assertThat(entry.type()).isEqualTo(AclEntryType.ALLOW);
        assertThat(entry.principal()).isEqualTo(view.getOwner());
    }

    /**
     * An owner-only entry alone does not make the file owner-only: on Windows a DACL that is not
     * protected still merges in the inheritable entries of the parent directory, so a permissive
     * ACE on the containing folder would keep other users able to read the credentials. This
     * gives the directory an entry every child inherits and asserts it does not survive on the
     * credentials file, which the entry count alone would not establish.
     */
    @Test
    public void testCredentialsFileDoesNotInheritPermissionsFromItsDirectory() throws IOException {
        final UserPrincipal everyone = lookupEveryone();
        assumeTrue(everyone != null,
                "The well-known 'Everyone' group is named differently on this system");

        final AclFileAttributeView homeView = Files.getFileAttributeView(home, AclFileAttributeView.class);
        final List<AclEntry> homeAcl = new ArrayList<>(homeView.getAcl());
        homeAcl.add(AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(everyone)
                .setPermissions(AclEntryPermission.READ_DATA, AclEntryPermission.READ_ATTRIBUTES,
                        AclEntryPermission.READ_ACL)
                .setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT)
                .build());
        homeView.setAcl(homeAcl);

        final Config config = new Config();
        config.setAcrCurrentHomePath(home);
        new FileCredentialProvider(config).store("dev/password", "s3cr3t");

        final AclFileAttributeView view =
                Files.getFileAttributeView(home.resolve("credentials.json"), AclFileAttributeView.class);
        assertThat(view.getAcl())
                .as("The entry inherited from the containing directory must not remain effective")
                .extracting(acl -> acl.principal().getName())
                .containsExactly(view.getOwner().getName());
    }

    private static UserPrincipal lookupEveryone() {
        try {
            return FileSystems.getDefault().getUserPrincipalLookupService()
                    .lookupPrincipalByName("Everyone");
        } catch (IOException ex) {
            return null;
        }
    }
}
