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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.v1.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v1.beans.SearchOver;
import io.apicurio.registry.rest.v1.beans.SortOrder;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
public class RegistryStoragePerformanceTest {
    
    private static final int NUM_ARTIFACTS = 50000;
//    private static final int NUM_VERSIONS = 5;
    
    private static final String OPENAPI_CONTENT_TEMPLATE = "{" + 
            "    \"openapi\": \"3.0.2\"," + 
            "    \"info\": {" + 
            "        \"title\": \"TITLE\"," + 
            "        \"version\": \"VERSION\"," + 
            "        \"description\": \"DESCRIPTION\"" + 
            "    }" + 
            "}";
    
    @Inject
    @Current
    RegistryStorage storage;

    protected RegistryStorage getStorage() {
        return storage;
    }

    private boolean isTestEnabled() {
        return "enabled".equals(System.getProperty(RegistryStoragePerformanceTest.class.getSimpleName()));
    }
    
    @Test
    public void testStoragePerformance() throws Exception {
        if (!isTestEnabled()) {
            return;
        }

        System.out.println("========================================================================");
        System.out.println("= Running storage performance test.  Please wait...                    =");
        System.out.println("========================================================================");

        String artifactIdPrefix = "testStoragePerformance-";
        
        long startCreate = System.currentTimeMillis();
        for (int idx = 1; idx <= NUM_ARTIFACTS; idx++) {
            String artifactId = artifactIdPrefix + idx;
            String title = "API " + artifactId;
            String description = "Number " + idx + " all time on the top APIs list.";
            List<String> labels = new ArrayList<String>();
            labels.add("the-label");
            labels.add("label-" + idx);
            Map<String, String> properties = new HashMap<>();
            properties.put("key", "value");
            properties.put("key-" + idx, "value-" + idx);
            ContentHandle content = ContentHandle.create(
                    OPENAPI_CONTENT_TEMPLATE
                        .replaceAll("TITLE", title)
                        .replaceAll("DESCRIPTION", description)
                        .replaceAll("VERSION", String.valueOf(idx)));
            EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto(title, description, labels,
                    properties);
            storage.createArtifactWithMetadata(artifactId, ArtifactType.OPENAPI, content, metaData).toCompletableFuture().get();
            
            System.out.print(".");
            if (idx % 100 == 0) {
                System.out.println(" " + idx);
            }
        }
        long endCreate = System.currentTimeMillis();
        
        long startGetArtifact = System.currentTimeMillis();
        StoredArtifact storedArtifact = storage.getArtifact(artifactIdPrefix + "77");
        long endGetArtifact = System.currentTimeMillis();
        Assertions.assertNotNull(storedArtifact);

        long startGetArtifactMetaData = System.currentTimeMillis();
        ArtifactMetaDataDto dto = storage.getArtifactMetaData(artifactIdPrefix + "998");
        long endGetArtifactMetaData = System.currentTimeMillis();
        Assertions.assertNotNull(dto);

        long startAllSearch = System.currentTimeMillis();
        ArtifactSearchResults results = storage.searchArtifacts(null, 0, 20, SearchOver.everything, SortOrder.asc);
        long endAllSearch = System.currentTimeMillis();
        Assertions.assertNotNull(results);
        Assertions.assertEquals(NUM_ARTIFACTS, results.getCount());
        
        long startNameSearch = System.currentTimeMillis();
        results = storage.searchArtifacts("testStoragePerformance-9999", 0, 10, SearchOver.name, SortOrder.asc);
        long endNameSearch = System.currentTimeMillis();
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());

        long startAllNameSearch = System.currentTimeMillis();
        results = storage.searchArtifacts("testStoragePerformance", 0, 10, SearchOver.name, SortOrder.asc);
        long endAllNameSearch = System.currentTimeMillis();
        Assertions.assertNotNull(results);
        Assertions.assertEquals(NUM_ARTIFACTS, results.getCount());

        long startLabelSearch = System.currentTimeMillis();
        results = storage.searchArtifacts("label-" + (NUM_ARTIFACTS-1), 0, 10, SearchOver.labels, SortOrder.asc);
        long endLabelSearch = System.currentTimeMillis();
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());

        long startAllLabelSearch = System.currentTimeMillis();
        results = storage.searchArtifacts("the-label", 0, 10, SearchOver.labels, SortOrder.asc);
        long endAllLabelSearch = System.currentTimeMillis();
        Assertions.assertNotNull(results);
        Assertions.assertEquals(NUM_ARTIFACTS, results.getCount());

        long startEverythingSearch = System.currentTimeMillis();
        results = storage.searchArtifacts("test", 0, 10, SearchOver.everything, SortOrder.asc);
        long endEverythingSearch = System.currentTimeMillis();
        Assertions.assertNotNull(results);
        Assertions.assertEquals(NUM_ARTIFACTS, results.getCount());

        System.out.println("========================================================================");
        System.out.println("= Storage Performance Results                                          =");
        System.out.println("=----------------------------------------------------------------------=");
        System.out.println("| Time to create " + NUM_ARTIFACTS + " artifacts: " + (endCreate - startCreate) + "ms");
        System.out.println("| ");
        System.out.println("| Get Artifact Content:   " + (endGetArtifact - startGetArtifact) + "ms");
        System.out.println("| Get Artifact Meta-Data: " + (endGetArtifactMetaData - startGetArtifactMetaData) + "ms");
        System.out.println("| ");
        System.out.println("| All Artifact Search:    " + (endAllSearch - startAllSearch) + "ms");
        System.out.println("| Single Name Search:     " + (endNameSearch - startNameSearch) + "ms");
        System.out.println("| All Name Search:        " + (endAllNameSearch - startAllNameSearch) + "ms");
        System.out.println("| Label Search:           " + (endLabelSearch - startLabelSearch) + "ms");
        System.out.println("| All Label Search:       " + (endAllLabelSearch - startAllLabelSearch) + "ms");
        System.out.println("| Everything Search:      " + (endEverythingSearch - startEverythingSearch) + "ms");
        System.out.println("========================================================================");
    }

}
