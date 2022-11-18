/*
 * Copyright 2022 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.noprofile.storage;

import static io.apicurio.registry.utils.tests.TestUtils.retry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import javax.inject.Inject;

import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.RegistryStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;

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
        String artifactId1 = TestUtils.generateArtifactId();
        createArtifact(artifactId1, "JSON", "{}");

        int size = getStorage().getArtifactIds(null).size();

        // Create 2 version of an artifact and one other artifact
        ArtifactMetaDataDto meta1 = getStorage().createArtifact(GROUP_ID, ARTIFACT_ID_1, null, ArtifactType.JSON, ContentHandle.create("content1"), null);
        this.waitForArtifact(GROUP_ID, ARTIFACT_ID_1);
        ArtifactMetaDataDto meta2 = getStorage().updateArtifact(GROUP_ID, ARTIFACT_ID_1, null, ArtifactType.JSON, ContentHandle.create("content2"), null);
        this.waitForGlobalId(meta2.getGlobalId());
        getStorage().createArtifact(GROUP_ID, ARTIFACT_ID_2, null, ArtifactType.AVRO, ContentHandle.create("content3"), null);
        this.waitForArtifact(GROUP_ID, ARTIFACT_ID_2);

        assertEquals(size + 2, getStorage().getArtifactIds(null).size());
        assertTrue(getStorage().getArtifactIds(null).contains(ARTIFACT_ID_1));

        StoredArtifactDto a1 = getStorage().getArtifact(GROUP_ID, ARTIFACT_ID_1);
        assertNotNull(a1);
        assertNotNull(a1.getGlobalId());
        assertNotNull(a1.getVersion());
        assertNotNull(a1.getContent());

        ArtifactMetaDataDto metaLatest = getStorage().getArtifactMetaData(GROUP_ID, ARTIFACT_ID_1);
        assertEquals(meta2, metaLatest);

        List<String> versions = getStorage().getArtifactVersions(GROUP_ID, ARTIFACT_ID_1);
        assertEquals(2, versions.size());
        assertTrue(versions.contains(a1.getVersion()));

        assertEquals(a1, getStorage().getArtifact(GROUP_ID, ARTIFACT_ID_1));

        // define name in an older version metadata
        getStorage().updateArtifactVersionMetaData(GROUP_ID, ARTIFACT_ID_1, meta1.getVersion(),
                EditableArtifactMetaDataDto.builder().name("foo").build());

        // update can be async
        retry(() -> {
            ArtifactVersionMetaDataDto vmeta1 = getStorage().getArtifactVersionMetaData(GROUP_ID, ARTIFACT_ID_1, meta1.getVersion());
            ArtifactVersionMetaDataDto vmeta2 = getStorage().getArtifactVersionMetaData(GROUP_ID, ARTIFACT_ID_1, meta2.getVersion());
            assertNotEquals(vmeta1, vmeta2);
            assertEquals("foo", vmeta1.getName());
            assertNull(vmeta2.getName());
            return null;
        });

        List<String> deleted = getStorage().deleteArtifact(GROUP_ID, ARTIFACT_ID_1);
        assertEquals(2, deleted.size());
        assertTrue(deleted.contains(a1.getVersion()));
        // delete can be async
        retry(() -> {
            try {
                getStorage().getArtifactMetaData(GROUP_ID, ARTIFACT_ID_1);
                fail();
            } catch (ArtifactNotFoundException ex) {
                // ok
            }

            List<String> set = getStorage().deleteArtifact(GROUP_ID, ARTIFACT_ID_2);
            assertEquals(1, set.size());
            return set;
        });
    }

    @Test
    public void testRules() throws Exception {
        getStorage().createArtifact(GROUP_ID, ARTIFACT_ID_3, null, ArtifactType.JSON, ContentHandle.create("content1"), null);

        this.waitForArtifact(GROUP_ID, ARTIFACT_ID_3);

        assertEquals(0, getStorage().getArtifactRules(GROUP_ID, ARTIFACT_ID_3).size());
        assertEquals(0, getStorage().getGlobalRules().size());

        getStorage().createArtifactRule(GROUP_ID, ARTIFACT_ID_3, RuleType.VALIDITY,
                RuleConfigurationDto.builder().configuration("config").build());

        getStorage().createGlobalRule(RuleType.VALIDITY,
                RuleConfigurationDto.builder().configuration("config").build());

        TestUtils.retry(() -> {
            assertEquals(1, getStorage().getArtifactRules(GROUP_ID, ARTIFACT_ID_3).size());
            assertTrue(getStorage().getArtifactRules(GROUP_ID, ARTIFACT_ID_3).contains(RuleType.VALIDITY));

            assertEquals("config", getStorage().getArtifactRule(GROUP_ID, ARTIFACT_ID_3, RuleType.VALIDITY).getConfiguration());

            assertEquals(1, getStorage().getGlobalRules().size());
            assertTrue(getStorage().getGlobalRules().contains(RuleType.VALIDITY));

            return null;
        });

        getStorage().deleteArtifact(GROUP_ID, ARTIFACT_ID_3);
    }

    @Test
    public void testLimitGetArtifactIds() throws Exception {
        final String testId0 = TestUtils.generateArtifactId();
        final String testId1 = TestUtils.generateArtifactId();
        final String testId2 = TestUtils.generateArtifactId();

        try {
            getStorage()
                    .createArtifact(GROUP_ID, testId0, null, ArtifactType.JSON, ContentHandle.create("{}"), null);
            this.waitForArtifact(GROUP_ID, testId0);

            int size = getStorage().getArtifactIds(null).size();

            // Create 2 artifacts
            getStorage()
                    .createArtifact(GROUP_ID, testId1, null, ArtifactType.JSON, ContentHandle.create("{}"), null);
            getStorage()
                    .createArtifact(GROUP_ID, testId2, null, ArtifactType.JSON, ContentHandle.create("{}"), null);

            this.waitForArtifact(GROUP_ID, testId1);
            this.waitForArtifact(GROUP_ID, testId2);

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
