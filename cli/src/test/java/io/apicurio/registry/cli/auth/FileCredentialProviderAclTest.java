package io.apicurio.registry.cli.auth;

import io.apicurio.registry.cli.config.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

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
}
