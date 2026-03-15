package io.apicurio.registry.storage.impl.polling;

import io.apicurio.registry.storage.impl.gitops.GitOpsRegistryStorage;
import io.apicurio.registry.storage.impl.polling.sql.BlueSqlStorage;
import io.apicurio.registry.storage.impl.polling.sql.GreenSqlStorage;
import io.apicurio.registry.storage.util.GitopsTestProfile;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the blue and green H2 databases are truly isolated.
 * Deleting data from one must not affect the other.
 */
@QuarkusTest
@TestProfile(GitopsTestProfile.class)
@ResourceLock("blue-green-database")
public class BlueGreenStorageIsolationTest {

    @Inject
    BlueSqlStorage blue;

    @Inject
    GreenSqlStorage green;

    @Inject
    GitOpsRegistryStorage gitOpsStorage;

    @Test
    void deleteAllUserDataOnOneDoesNotAffectTheOther() {
        // Hold the refresh lock to prevent the GitOps scheduler from polling and
        // reconfiguring shared repositories while we operate on blue/green directly.
        //var refreshLock = gitOpsStorage.getRefreshLock();
        //refreshLock.lock();
        //try {
            blue.initialize();
            green.initialize();

            // Clean up any data from other tests sharing the same Quarkus instance
            blue.deleteAllUserData();
            green.deleteAllUserData();

            // Import data into both
            importTestArtifact(blue, "blue-artifact");
            importTestArtifact(green, "green-artifact");

            // Delete all data from blue — should NOT affect green
            blue.deleteAllUserData();

            // Import a marker into green to verify it's still operational
            importTestArtifact(green, "green-marker");

            // Green should have both the original and the marker
            assertEquals(Set.of("green-artifact", "green-marker"), green.getArtifactIds(10),
                    "Green should be untouched after deleting blue's data");

            // Import into blue to verify it was actually cleared
            importTestArtifact(blue, "blue-new");
            assertEquals(Set.of("blue-new"), blue.getArtifactIds(10),
                    "Blue should only have the newly imported artifact after deleteAllUserData");

            // Clean up after ourselves to avoid interfering with other tests
            blue.deleteAllUserData();
            green.deleteAllUserData();
        //} finally {
        //    refreshLock.unlock();
        //}
    }

    private void importTestArtifact(io.apicurio.registry.storage.RegistryStorage storage, String artifactId) {
        if (!storage.isGroupExists("test")) {
            var group = new GroupEntity();
            group.groupId = "test";
            storage.importGroup(group);
        }

        var artifact = new ArtifactEntity();
        artifact.groupId = "test";
        artifact.artifactId = artifactId;
        artifact.artifactType = "JSON";
        storage.importArtifact(artifact);

        var content = new ContentEntity();
        content.contentId = Math.abs((long) artifactId.hashCode());
        content.contentHash = artifactId;
        content.canonicalHash = artifactId;
        content.contentBytes = "{}".getBytes();
        content.artifactType = "JSON";
        content.contentType = "application/json";
        storage.importContent(content);

        var version = new ArtifactVersionEntity();
        version.groupId = "test";
        version.artifactId = artifactId;
        version.version = "1";
        version.versionOrder = 1;
        version.globalId = Math.abs((long) artifactId.hashCode());
        version.contentId = content.contentId;
        version.state = io.apicurio.registry.types.VersionState.ENABLED;
        storage.importArtifactVersion(version);
    }
}
