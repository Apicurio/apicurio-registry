package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.noprofile.storage.AbstractRegistryStorageTest;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.impl.sql.jdb.RuntimeSqlException;
import io.apicurio.registry.storage.impl.sql.upgrader.DuplicateVersionOrderUpgrader;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Locale;

@QuarkusTest
public class DefaultRegistryStorageTest extends AbstractRegistryStorageTest {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    HandleFactory handles;

    @Inject
    SqlStatements sqlStatements;

    /**
     * @see AbstractRegistryStorageTest#storage()
     */
    @Override
    protected RegistryStorage storage() {
        return storage;
    }

    /**
     * The import path writes versionOrder verbatim, so it hands the database a duplicate order
     * without a race. The version name and globalId differ from the existing row, which leaves
     * UQ_versions_3 as the only constraint the insert can break.
     */
    @Test
    public void testDuplicateVersionOrderIsRejected() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        ArtifactVersionMetaDataDto first = createFirstVersion(groupId, artifactId);
        Assertions.assertEquals(1, first.getVersionOrder());

        RuntimeSqlException e = Assertions.assertThrows(RuntimeSqlException.class,
                () -> storage.importArtifactVersion(duplicateOf(first, "2")));
        // PostgreSQL folds the unquoted constraint name to lower case; the other dialects keep it.
        Assertions.assertTrue(e.getMessage().toLowerCase(Locale.ROOT).contains("uq_versions_3"),
                "Expected a UQ_versions_3 violation, got: " + e.getMessage());
        Assertions.assertEquals(1L, storage.countArtifactVersions(groupId, artifactId));
    }

    /**
     * Seeds the state a registry is in after losing the first-version race: UQ_versions_3 absent
     * and two versions of one artifact sharing a versionOrder. The 110 to 111 upgrade must then
     * stop before ADD CONSTRAINT, name both rows and leave db_version at 110, and it must go through
     * once the operator has removed the duplicate.
     */
    @Test
    public void testUpgradeTo111FailsOnDuplicateVersionOrder() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        ArtifactVersionMetaDataDto first = createFirstVersion(groupId, artifactId);
        String currentVersion = databaseVersion();

        boolean constraintRestored = false;
        execute("ALTER TABLE versions DROP CONSTRAINT UQ_versions_3");
        try {
            storage.importArtifactVersion(duplicateOf(first, "2"));
            execute("UPDATE apicurio SET propValue = '110' WHERE propName = 'db_version'");

            IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
                    this::upgradeTo111);
            Assertions.assertTrue(e.getMessage().contains("versions share a versionOrder"), e.getMessage());
            Assertions.assertTrue(e.getMessage().contains("groupId=" + groupId + ", artifactId=" + artifactId
                    + ", version=1, versionOrder=1"), e.getMessage());
            Assertions.assertTrue(e.getMessage().contains("groupId=" + groupId + ", artifactId=" + artifactId
                    + ", version=2, versionOrder=1"), e.getMessage());
            Assertions.assertEquals("110", databaseVersion());

            storage.deleteArtifactVersion(groupId, artifactId, "2");
            upgradeTo111();
            constraintRestored = true;
            Assertions.assertEquals("111", databaseVersion());
            // The constraint is back, so the same duplicate is rejected again.
            RuntimeSqlException rejected = Assertions.assertThrows(RuntimeSqlException.class,
                    () -> storage.importArtifactVersion(duplicateOf(first, "3")));
            Assertions.assertTrue(rejected.getMessage().toLowerCase(Locale.ROOT).contains("uq_versions_3"),
                    "Expected a UQ_versions_3 violation, got: " + rejected.getMessage());
            Assertions.assertEquals(1L, storage.countArtifactVersions(groupId, artifactId));
        } finally {
            // Put the shared store back the way the other tests expect it.
            if (!constraintRestored) {
                execute("DELETE FROM versions WHERE groupId = '" + groupId + "' AND artifactId = '"
                        + artifactId + "' AND version <> '1'");
                execute("ALTER TABLE versions ADD CONSTRAINT UQ_versions_3 UNIQUE (groupId, artifactId,"
                        + " versionOrder)");
            }
            execute("UPDATE apicurio SET propValue = '" + currentVersion + "' WHERE propName = 'db_version'");
        }
    }

    private ArtifactVersionMetaDataDto createFirstVersion(String groupId, String artifactId) {
        return storage.createArtifact(groupId, artifactId, ArtifactType.OPENAPI, null, "1",
                ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                        .content(ContentHandle.create(OPENAPI_CONTENT)).build(),
                null, Collections.emptyList(), false, false, null).getValue();
    }

    private ArtifactVersionEntity duplicateOf(ArtifactVersionMetaDataDto existing, String version) {
        ArtifactVersionEntity duplicate = new ArtifactVersionEntity();
        duplicate.globalId = storage.nextGlobalId();
        duplicate.groupId = existing.getGroupId();
        duplicate.artifactId = existing.getArtifactId();
        duplicate.version = version;
        duplicate.versionOrder = existing.getVersionOrder();
        duplicate.state = VersionState.ENABLED;
        duplicate.contentId = existing.getContentId();
        // The entity's epoch default (0L) falls below MySQL TIMESTAMP's 1970-01-01 00:00:01 floor,
        // so MySQL would reject the insert for the datetime before UQ_versions_3 is evaluated.
        duplicate.createdOn = System.currentTimeMillis();
        duplicate.modifiedOn = System.currentTimeMillis();
        return duplicate;
    }

    /** Runs the real 111 scripts the way AbstractSqlRegistryStorage.upgradeDatabaseRaw does. */
    private void upgradeTo111() {
        handles.withHandleNoException(handle -> {
            for (String statement : sqlStatements.databaseUpgrade(110, 111)) {
                if (statement.startsWith("UPGRADER:")) {
                    String className = statement.substring("UPGRADER:".length()).trim();
                    Assertions.assertEquals(DuplicateVersionOrderUpgrader.class.getName(), className);
                    ((IDbUpgrader) Class.forName(className).getConstructor().newInstance()).upgrade(handle);
                } else {
                    handle.createUpdate(statement).execute();
                }
            }
            return null;
        });
    }

    private void execute(String sql) {
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sql).execute();
            return null;
        });
    }

    private String databaseVersion() {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery("SELECT propValue FROM apicurio WHERE propName = 'db_version'")
                    .mapTo(String.class).one();
        });
    }

}
