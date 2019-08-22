package io.apicurio.registry.storage;

import io.apicurio.registry.rest.beans.ArtifactType;
import io.apicurio.registry.storage.impl.jpa.JPA;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Collections;
import java.util.SortedSet;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class RegistryStorageSmokeTest {

    private static Logger log = LoggerFactory.getLogger(RegistryStorageSmokeTest.class);

    @Inject
    @JPA
    private RegistryStorage storage;

    @Test
    public void testArtifactsAndMeta() {
        final String ARTIFACT_ID_1 = "artifactId1";
        final String ARTIFACT_ID_2 = "artifactId2";

        assertEquals(Collections.emptySet(), storage.getArtifactIds());
        // Create 2 version of an artifact and one other artifact
        ArtifactMetaDataDto meta1 = storage.createArtifact(ARTIFACT_ID_1, ArtifactType.json, "content1");
        ArtifactMetaDataDto meta2 = storage.updateArtifact(ARTIFACT_ID_1, ArtifactType.json, "content2");
        storage.createArtifact(ARTIFACT_ID_2, ArtifactType.avro, "content3");

        assertEquals(2, storage.getArtifactIds().size());
        assertTrue(storage.getArtifactIds().contains(ARTIFACT_ID_1));

        StoredArtifact a1 = storage.getArtifact(ARTIFACT_ID_1);
        assertNotNull(a1);
        assertNotNull(a1.getId());
        assertNotNull(a1.getVersion());
        assertNotNull(a1.getContent());

        ArtifactMetaDataDto metaLatest = storage.getArtifactMetaData(ARTIFACT_ID_1);
        assertEquals(meta2, metaLatest);

        SortedSet<Long> versions = storage.getArtifactVersions(ARTIFACT_ID_1);
        assertEquals(2, versions.size());
        assertTrue(versions.contains(a1.getVersion()));

        assertEquals(a1, storage.getArtifact(ARTIFACT_ID_1));

        // define name in an older version metadata
        storage.updateArtifactVersionMetaData(ARTIFACT_ID_1, meta1.getVersion(),
                EditableArtifactMetaDataDto.builder().name("foo").build());
        ArtifactVersionMetaDataDto vmeta1 = storage.getArtifactVersionMetaData(ARTIFACT_ID_1, meta1.getVersion());
        ArtifactVersionMetaDataDto vmeta2 = storage.getArtifactVersionMetaData(ARTIFACT_ID_1, meta2.getVersion());
        assertNotEquals(vmeta1, vmeta2);
        assertEquals("foo", vmeta1.getName());
        assertNull(vmeta2.getName());

        SortedSet<Long> deleted = storage.deleteArtifact(ARTIFACT_ID_1);
        assertEquals(2, deleted.size());
        assertTrue(deleted.contains(a1.getVersion()));

        try {
            storage.getArtifactMetaData(ARTIFACT_ID_1);
            fail();
        } catch (ArtifactNotFoundException ex) {
            // ok
        }

        deleted = storage.deleteArtifact(ARTIFACT_ID_2);
        assertEquals(1, deleted.size());
    }

    @Test
    public void testRules() {
        final String ARTIFACT_ID_1 = "artifactId3";

        assertEquals(Collections.emptySet(), storage.getArtifactIds());
        storage.createArtifact(ARTIFACT_ID_1, ArtifactType.json, "content1");

        assertEquals(0, storage.getArtifactRules(ARTIFACT_ID_1).size());
        assertEquals(0, storage.getGlobalRules().size());

        storage.createArtifactRule(ARTIFACT_ID_1, "rule",
                RuleConfigurationDto.builder().configuration("config").build());

        storage.createGlobalRule("global_rule",
                RuleConfigurationDto.builder().configuration("config").build());

        assertEquals(1, storage.getArtifactRules(ARTIFACT_ID_1).size());
        assertTrue(storage.getArtifactRules(ARTIFACT_ID_1).contains("rule"));

        assertEquals("config", storage.getArtifactRule(ARTIFACT_ID_1, "rule").getConfiguration());

        assertEquals(1, storage.getGlobalRules().size());
        assertTrue(storage.getGlobalRules().contains("global_rule"));

        storage.deleteArtifact(ARTIFACT_ID_1);
    }
}
