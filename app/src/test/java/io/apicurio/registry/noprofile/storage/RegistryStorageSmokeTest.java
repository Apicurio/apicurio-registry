package io.apicurio.registry.noprofile.storage;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@QuarkusTest
public class RegistryStorageSmokeTest extends AbstractResourceTestBase {

    private static final String GROUP_ID = RegistryStorageSmokeTest.class.getSimpleName();

    static final String ARTIFACT_ID_1 = "artifactId1";
    static final String ARTIFACT_ID_2 = "artifactId2";
    static final String ARTIFACT_ID_3 = "artifactId3";

    @Inject
    @Current
    RegistryStorage storage;

    protected RegistryStorage getStorage() {
        return storage;
    }

    private void delete(String artifactId, boolean rule) {
        try {
            if (rule) {
                getStorage().deleteArtifactRules(GROUP_ID, artifactId);
            } else {
                getStorage().deleteArtifact(GROUP_ID, artifactId);
            }
        } catch (ArtifactNotFoundException ignored) {
        }
    }

    @Override
    @BeforeEach
    public void beforeEach() throws Exception {
        super.beforeEach();

        getStorage().deleteGlobalRules();

        delete(ARTIFACT_ID_1, false);
        delete(ARTIFACT_ID_2, false);
        delete(ARTIFACT_ID_3, false);

        delete(ARTIFACT_ID_1, true);
        delete(ARTIFACT_ID_2, true);
        delete(ARTIFACT_ID_3, true);
    }

    @Test
    public void testArtifactsAndMeta() throws Exception {
        int size = getStorage().getArtifactIds(null).size();

        // Create 2 artifacts
        String artifactId1 = TestUtils.generateArtifactId();
        String artifactId2 = TestUtils.generateArtifactId();

        // Create artifact 1
        EditableArtifactMetaDataDto artifactMetaData1 = EditableArtifactMetaDataDto.builder().build();
        ContentWrapperDto versionContent1 = ContentWrapperDto.builder()
                .content(ContentHandle.create("content1")).contentType(ContentTypes.APPLICATION_JSON).build();
        EditableVersionMetaDataDto versionMetaData1 = EditableVersionMetaDataDto.builder().build();
        ArtifactVersionMetaDataDto vmdDto1_1 = getStorage().createArtifact(GROUP_ID, artifactId1,
                ArtifactType.JSON, artifactMetaData1, null, versionContent1, versionMetaData1, List.of())
                .getRight();
        // Create version 2 (for artifact 1)
        ArtifactVersionMetaDataDto vmdDto1_2 = getStorage().createArtifactVersion(GROUP_ID, artifactId1, null,
                ArtifactType.JSON, versionContent1, versionMetaData1, List.of());

        // Create artifact 2
        EditableArtifactMetaDataDto artifactMetaData2 = EditableArtifactMetaDataDto.builder().build();
        ContentWrapperDto versionContent2 = ContentWrapperDto.builder()
                .content(ContentHandle.create("content2")).contentType(ContentTypes.APPLICATION_JSON).build();
        EditableVersionMetaDataDto versionMetaData2 = EditableVersionMetaDataDto.builder().build();
        getStorage().createArtifact(GROUP_ID, artifactId2, ArtifactType.AVRO, artifactMetaData2, null,
                versionContent2, versionMetaData2, List.of()).getRight();

        assertEquals(size + 2, getStorage().getArtifactIds(null).size());
        assertTrue(getStorage().getArtifactIds(null).contains(artifactId1));

        StoredArtifactVersionDto a1 = getStorage().getArtifactVersionContent(GROUP_ID, artifactId1,
                vmdDto1_2.getVersion());
        assertNotNull(a1);
        assertNotNull(a1.getGlobalId());
        assertNotNull(a1.getVersion());
        assertNotNull(a1.getContent());

        GAV latestGAV = getStorage().getBranchTip(new GA(GROUP_ID, artifactId1), BranchId.LATEST,
                RetrievalBehavior.DEFAULT);
        ArtifactVersionMetaDataDto metaLatest = getStorage().getArtifactVersionMetaData(GROUP_ID, artifactId1,
                latestGAV.getRawVersionId());
        assertEquals(vmdDto1_2, metaLatest);

        List<String> versions = getStorage().getArtifactVersions(GROUP_ID, artifactId1);
        assertEquals(2, versions.size());
        assertTrue(versions.contains(a1.getVersion()));

        assertEquals(a1,
                getStorage().getArtifactVersionContent(GROUP_ID, artifactId1, vmdDto1_2.getVersion()));

        // define name in an older version metadata
        getStorage().updateArtifactVersionMetaData(GROUP_ID, artifactId1, vmdDto1_1.getVersion(),
                EditableVersionMetaDataDto.builder().name("foo").build());

        ArtifactVersionMetaDataDto vmeta1 = getStorage().getArtifactVersionMetaData(GROUP_ID, artifactId1,
                vmdDto1_1.getVersion());
        ArtifactVersionMetaDataDto vmeta2 = getStorage().getArtifactVersionMetaData(GROUP_ID, artifactId1,
                vmdDto1_2.getVersion());
        assertNotEquals(vmeta1, vmeta2);
        assertEquals("foo", vmeta1.getName());
        assertNull(vmeta2.getName());

        List<String> deleted = getStorage().deleteArtifact(GROUP_ID, artifactId1);
        assertEquals(2, deleted.size());
        assertTrue(deleted.contains(a1.getVersion()));

        try {
            getStorage().getArtifactMetaData(GROUP_ID, artifactId1);
            fail();
        } catch (ArtifactNotFoundException ex) {
            // ok
        }

        List<String> set = getStorage().deleteArtifact(GROUP_ID, artifactId2);
        assertEquals(1, set.size());
    }

    @Test
    public void testRules() throws Exception {
        String artifactId = TestUtils.generateArtifactId();

        // Create artifact
        EditableArtifactMetaDataDto artifactMetaData = EditableArtifactMetaDataDto.builder().build();
        ContentWrapperDto versionContent1 = ContentWrapperDto.builder()
                .content(ContentHandle.create("content1")).contentType(ContentTypes.APPLICATION_JSON).build();
        EditableVersionMetaDataDto versionMetaData = EditableVersionMetaDataDto.builder().build();
        getStorage().createArtifact(GROUP_ID, artifactId, ArtifactType.JSON, artifactMetaData, null,
                versionContent1, versionMetaData, List.of()).getRight();

        assertEquals(0, getStorage().getArtifactRules(GROUP_ID, artifactId).size());
        assertEquals(0, getStorage().getGlobalRules().size());

        getStorage().createArtifactRule(GROUP_ID, artifactId, RuleType.VALIDITY,
                RuleConfigurationDto.builder().configuration("config").build());

        getStorage().createGlobalRule(RuleType.VALIDITY,
                RuleConfigurationDto.builder().configuration("config").build());

        assertEquals(1, getStorage().getArtifactRules(GROUP_ID, artifactId).size());
        assertTrue(getStorage().getArtifactRules(GROUP_ID, artifactId).contains(RuleType.VALIDITY));

        assertEquals("config",
                getStorage().getArtifactRule(GROUP_ID, artifactId, RuleType.VALIDITY).getConfiguration());

        assertEquals(1, getStorage().getGlobalRules().size());
        assertTrue(getStorage().getGlobalRules().contains(RuleType.VALIDITY));

        getStorage().deleteArtifact(GROUP_ID, artifactId);
    }

    @Test
    public void testLimitGetArtifactIds() throws Exception {
        final String testId0 = TestUtils.generateArtifactId();
        final String testId1 = TestUtils.generateArtifactId();
        final String testId2 = TestUtils.generateArtifactId();

        try {
            ContentWrapperDto content = ContentWrapperDto.builder().content(ContentHandle.create("{}"))
                    .contentType(ContentTypes.APPLICATION_JSON).build();

            getStorage().createArtifact(GROUP_ID, testId0, ArtifactType.JSON, null, null, content, null,
                    List.of());

            int size = getStorage().getArtifactIds(null).size();

            // Create 2 artifacts
            getStorage().createArtifact(GROUP_ID, testId1, ArtifactType.JSON, null, null, content, null,
                    List.of());
            getStorage().createArtifact(GROUP_ID, testId2, ArtifactType.JSON, null, null, content, null,
                    List.of());

            int newSize = getStorage().getArtifactIds(null).size();
            int limitedSize = getStorage().getArtifactIds(1).size();

            assertEquals(size + 2, newSize);
            assertEquals(1, limitedSize);
        } finally {
            getStorage().deleteArtifact(GROUP_ID, testId1);
            getStorage().deleteArtifact(GROUP_ID, testId2);
        }
    }
}
