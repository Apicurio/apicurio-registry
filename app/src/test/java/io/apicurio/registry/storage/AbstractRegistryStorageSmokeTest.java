package io.apicurio.registry.storage;

import io.apicurio.registry.types.ArtifactType;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.SortedSet;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public abstract class AbstractRegistryStorageSmokeTest {

    private static Logger log = LoggerFactory.getLogger(AbstractRegistryStorageSmokeTest.class);

    abstract RegistryStorage getStorage();

    @Test
    public void testArtifactsAndMeta() {
        final String ARTIFACT_ID_1 = "artifactId1";
        final String ARTIFACT_ID_2 = "artifactId2";

        assertEquals(Collections.emptySet(), getStorage().getArtifactIds());
        // Create 2 version of an artifact and one other artifact
        ArtifactMetaDataDto meta1 = getStorage().createArtifact(ARTIFACT_ID_1, ArtifactType.json, "content1");
        ArtifactMetaDataDto meta2 = getStorage().updateArtifact(ARTIFACT_ID_1, ArtifactType.json, "content2");
        getStorage().createArtifact(ARTIFACT_ID_2, ArtifactType.avro, "content3");

        assertEquals(2, getStorage().getArtifactIds().size());
        assertTrue(getStorage().getArtifactIds().contains(ARTIFACT_ID_1));

        StoredArtifact a1 = getStorage().getArtifact(ARTIFACT_ID_1);
        assertNotNull(a1);
        assertNotNull(a1.getId());
        assertNotNull(a1.getVersion());
        assertNotNull(a1.getContent());

        ArtifactMetaDataDto metaLatest = getStorage().getArtifactMetaData(ARTIFACT_ID_1);
        assertEquals(meta2, metaLatest);

        SortedSet<Long> versions = getStorage().getArtifactVersions(ARTIFACT_ID_1);
        assertEquals(2, versions.size());
        assertTrue(versions.contains(a1.getVersion()));

        assertEquals(a1, getStorage().getArtifact(ARTIFACT_ID_1));

        // define name in an older version metadata
        getStorage().updateArtifactVersionMetaData(ARTIFACT_ID_1, meta1.getVersion(),
                EditableArtifactMetaDataDto.builder().name("foo").build());
        ArtifactVersionMetaDataDto vmeta1 = getStorage().getArtifactVersionMetaData(ARTIFACT_ID_1, meta1.getVersion());
        ArtifactVersionMetaDataDto vmeta2 = getStorage().getArtifactVersionMetaData(ARTIFACT_ID_1, meta2.getVersion());
        assertNotEquals(vmeta1, vmeta2);
        assertEquals("foo", vmeta1.getName());
        assertNull(vmeta2.getName());

        SortedSet<Long> deleted = getStorage().deleteArtifact(ARTIFACT_ID_1);
        assertEquals(2, deleted.size());
        assertTrue(deleted.contains(a1.getVersion()));

        try {
            getStorage().getArtifactMetaData(ARTIFACT_ID_1);
            fail();
        } catch (ArtifactNotFoundException ex) {
            // ok
        }

        deleted = getStorage().deleteArtifact(ARTIFACT_ID_2);
        assertEquals(1, deleted.size());
    }

    @Test
    public void testRules() {
        final String ARTIFACT_ID_1 = "artifactId3";

        assertEquals(Collections.emptySet(), getStorage().getArtifactIds());
        getStorage().createArtifact(ARTIFACT_ID_1, ArtifactType.json, "content1");

        assertEquals(0, getStorage().getArtifactRules(ARTIFACT_ID_1).size());
        assertEquals(0, getStorage().getGlobalRules().size());

        getStorage().createArtifactRule(ARTIFACT_ID_1, "rule",
                RuleConfigurationDto.builder().configuration("config").build());

        getStorage().createGlobalRule("global_rule",
                RuleConfigurationDto.builder().configuration("config").build());

        assertEquals(1, getStorage().getArtifactRules(ARTIFACT_ID_1).size());
        assertTrue(getStorage().getArtifactRules(ARTIFACT_ID_1).contains("rule"));

        assertEquals("config", getStorage().getArtifactRule(ARTIFACT_ID_1, "rule").getConfiguration());

        assertEquals(1, getStorage().getGlobalRules().size());
        assertTrue(getStorage().getGlobalRules().contains("global_rule"));

        getStorage().deleteArtifact(ARTIFACT_ID_1);
    }
}
