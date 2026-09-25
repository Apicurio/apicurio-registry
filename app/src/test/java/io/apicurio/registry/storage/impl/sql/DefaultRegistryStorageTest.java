package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.noprofile.storage.AbstractRegistryStorageTest;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.impl.sql.jdb.RuntimeSqlException;
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
        ArtifactVersionMetaDataDto first = storage.createArtifact(groupId, artifactId,
                ArtifactType.OPENAPI, null, "1",
                ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                        .content(ContentHandle.create(OPENAPI_CONTENT)).build(),
                null, Collections.emptyList(), false, false, null).getValue();
        Assertions.assertEquals(1, first.getVersionOrder());

        ArtifactVersionEntity duplicate = new ArtifactVersionEntity();
        duplicate.globalId = storage.nextGlobalId();
        duplicate.groupId = groupId;
        duplicate.artifactId = artifactId;
        duplicate.version = "2";
        duplicate.versionOrder = first.getVersionOrder();
        duplicate.state = VersionState.ENABLED;
        duplicate.contentId = first.getContentId();
        // The entity's epoch default (0L) falls below MySQL TIMESTAMP's 1970-01-01 00:00:01 floor,
        // so MySQL would reject the insert for the datetime before UQ_versions_3 is evaluated.
        duplicate.createdOn = System.currentTimeMillis();
        duplicate.modifiedOn = System.currentTimeMillis();

        RuntimeSqlException e = Assertions.assertThrows(RuntimeSqlException.class,
                () -> storage.importArtifactVersion(duplicate));
        // PostgreSQL folds the unquoted constraint name to lower case; the other dialects keep it.
        Assertions.assertTrue(e.getMessage().toLowerCase(Locale.ROOT).contains("uq_versions_3"),
                "Expected a UQ_versions_3 violation, got: " + e.getMessage());
        Assertions.assertEquals(1L, storage.countArtifactVersions(groupId, artifactId));
    }

}
