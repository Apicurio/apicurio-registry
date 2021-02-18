/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.storage;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.utils.tests.TestUtils.retry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.SortedSet;
import javax.inject.Inject;

@QuarkusTest
public class RegistryStorageSmokeTest extends AbstractResourceTestBase {

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
                getStorage().deleteArtifactRules(artifactId);
            } else {
                getStorage().deleteArtifact(artifactId);
            }
        } catch (ArtifactNotFoundException ignored) {
        }
    }

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
        createArtifact(artifactId1, ArtifactType.JSON, "{}");
        
        int size = getStorage().getArtifactIds(null).size();

        // Create 2 version of an artifact and one other artifact
        ArtifactMetaDataDto meta1 = ConcurrentUtil.result(getStorage().createArtifact(ARTIFACT_ID_1, ArtifactType.JSON, ContentHandle.create("content1")));
        this.waitForArtifact(ARTIFACT_ID_1);
        ArtifactMetaDataDto meta2 = ConcurrentUtil.result(getStorage().updateArtifact(ARTIFACT_ID_1, ArtifactType.JSON, ContentHandle.create("content2")));
        this.waitForGlobalId(meta2.getGlobalId());
        ConcurrentUtil.result(getStorage().createArtifact(ARTIFACT_ID_2, ArtifactType.AVRO, ContentHandle.create("content3")));
        this.waitForArtifact(ARTIFACT_ID_2);

        assertEquals(size + 2, getStorage().getArtifactIds(null).size());
        assertTrue(getStorage().getArtifactIds(null).contains(ARTIFACT_ID_1));

        StoredArtifact a1 = getStorage().getArtifact(ARTIFACT_ID_1);
        assertNotNull(a1);
        assertNotNull(a1.getGlobalId());
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

        // update can be async
        retry(() -> {
            ArtifactVersionMetaDataDto vmeta1 = getStorage().getArtifactVersionMetaData(ARTIFACT_ID_1, meta1.getVersion());
            ArtifactVersionMetaDataDto vmeta2 = getStorage().getArtifactVersionMetaData(ARTIFACT_ID_1, meta2.getVersion());
            assertNotEquals(vmeta1, vmeta2);
            assertEquals("foo", vmeta1.getName());
            assertNull(vmeta2.getName());
            return null;
        });

        // TODO uncomment this once search is implemented for all storages.  These tests are run against all storage variants.
//        final ArtifactSearchResults countSearchResult = storage.searchArtifacts("arti", 0, 1, SearchOver.everything, SortOrder.asc);
//
//        assertEquals(2, countSearchResult.getCount());
//        assertEquals(countSearchResult.getArtifacts().get(0).getId(), ARTIFACT_ID_1);
//
//        final ArtifactSearchResults ascendingSearchResults = storage.searchArtifacts("arti", 0, 10, SearchOver.everything, SortOrder.asc);
//
//        assertEquals(2, ascendingSearchResults.getCount());
//        assertEquals(ascendingSearchResults.getArtifacts().get(0).getId(), ARTIFACT_ID_1);
//
//        final ArtifactSearchResults descendingSearchResults = storage.searchArtifacts("arti", 0, 10, SearchOver.everything, SortOrder.desc);
//
//        assertEquals(2, descendingSearchResults.getCount());
//        assertEquals(descendingSearchResults.getArtifacts().get(0).getId(), ARTIFACT_ID_2);

        SortedSet<Long> deleted = getStorage().deleteArtifact(ARTIFACT_ID_1);
        assertEquals(2, deleted.size());
        assertTrue(deleted.contains(a1.getVersion()));
        // delete can be async
        retry(() -> {
            try {
                getStorage().getArtifactMetaData(ARTIFACT_ID_1);
                fail();
            } catch (ArtifactNotFoundException ex) {
                // ok
            }

            SortedSet<Long> set = getStorage().deleteArtifact(ARTIFACT_ID_2);
            assertEquals(1, set.size());
            return set;
        });
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testRules() throws Exception {
        ConcurrentUtil.result(getStorage().createArtifact(ARTIFACT_ID_3, ArtifactType.JSON, ContentHandle.create("content1")));
        
        this.waitForArtifact(ARTIFACT_ID_3);

        assertEquals(0, getStorage().getArtifactRules(ARTIFACT_ID_3).size());
        assertEquals(0, getStorage().getGlobalRules().size());

        getStorage().createArtifactRule(ARTIFACT_ID_3, RuleType.VALIDITY,
                RuleConfigurationDto.builder().configuration("config").build());

        getStorage().createGlobalRule(RuleType.VALIDITY,
                RuleConfigurationDto.builder().configuration("config").build());

        TestUtils.retry(() -> {
            assertEquals(1, getStorage().getArtifactRules(ARTIFACT_ID_3).size());
            assertTrue(getStorage().getArtifactRules(ARTIFACT_ID_3).contains(RuleType.VALIDITY));

            assertEquals("config", getStorage().getArtifactRule(ARTIFACT_ID_3, RuleType.VALIDITY).getConfiguration());

            assertEquals(1, getStorage().getGlobalRules().size());
            assertTrue(getStorage().getGlobalRules().contains(RuleType.VALIDITY));
            
            return null;
        });

        getStorage().deleteArtifact(ARTIFACT_ID_3);
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testLimitGetArtifactIds() throws Exception {
        final String testId0 = TestUtils.generateArtifactId();
        final String testId1 = TestUtils.generateArtifactId();
        final String testId2 = TestUtils.generateArtifactId();

        try {
            ConcurrentUtil.result(getStorage()
                    .createArtifact(testId0, ArtifactType.JSON, ContentHandle.create("{}")));
            this.waitForArtifact(testId0);

            int size = getStorage().getArtifactIds(null).size();

            // Create 2 artifacts
            ConcurrentUtil.result(getStorage()
                    .createArtifact(testId1, ArtifactType.JSON, ContentHandle.create("{}")));
            ConcurrentUtil.result(getStorage()
                    .createArtifact(testId2, ArtifactType.JSON, ContentHandle.create("{}")));

            this.waitForArtifact(testId1);
            this.waitForArtifact(testId2);

            int newSize = getStorage().getArtifactIds(null).size();
            int limitedSize = getStorage().getArtifactIds(1).size();

            assertEquals(size + 2, newSize);
            assertEquals(1, limitedSize);
        } finally {
            getStorage().deleteArtifact(testId1);
            getStorage().deleteArtifact(testId2);
        }
    }
}
