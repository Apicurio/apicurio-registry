/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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

package io.apicurio.registry;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
public class ArtifactSearchTest extends AbstractResourceTestBase {
    
    private static final String OPENAPI_CONTENT_TEMPLATE = "{\r\n" + 
            "    \"openapi\": \"3.0.2\",\r\n" + 
            "    \"info\": {\r\n" + 
            "        \"title\": \"TITLE\",\r\n" + 
            "        \"version\": \"1.0.0\",\r\n" + 
            "        \"description\": \"DESCRIPTION\"\r\n" + 
            "    }\r\n" + 
            "}";

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    void testCaseInsensitiveSearch() throws Exception {

        // warm-up
        client.listArtifacts();

        String artifactId = UUID.randomUUID().toString();
        String title = "testCaseInsensitiveSearch";
        String description = "The quick brown FOX jumped over the Lazy dog.";
        String content = OPENAPI_CONTENT_TEMPLATE.replace("TITLE", title).replace("DESCRIPTION", description);
        ByteArrayInputStream artifactData = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        ArtifactMetaData amd = client.createArtifact(artifactId, ArtifactType.OPENAPI, artifactData);
        long id = amd.getGlobalId();

        this.waitForGlobalId(id);

        // Search against the name, with the exact name of the artifact
        ArtifactSearchResults results = client.searchArtifacts(title, SearchOver.name, SortOrder.asc, 0, 10);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());

        // Update the meta-data for the artifact
        EditableMetaData metaData = new EditableMetaData();
        metaData.setName(title);
        metaData.setDescription(description);
        metaData.setLabels(Collections.singletonList("testCaseInsensitiveSearchLabel"));
        metaData.setProperties(Collections.singletonMap("testCaseInsensitiveSearchKey", "testCaseInsensitiveSearchValue"));
        client.updateArtifactMetaData(artifactId, metaData);

        TestUtils.retry(() -> {
            // Now try various cases when seaching by labels and properties
            ArtifactSearchResults ires = client.searchArtifacts("testCaseInsensitiveSearchLabel", SearchOver.labels, SortOrder.asc, 0, 10);
            Assertions.assertNotNull(ires);
            Assertions.assertEquals(1, ires.getCount());
            ires = client.searchArtifacts("testCaseInsensitiveSearchLabel".toLowerCase(), SearchOver.labels, SortOrder.asc, 0, 10);
            Assertions.assertNotNull(ires);
            Assertions.assertEquals(1, ires.getCount());
            ires = client.searchArtifacts("testCaseInsensitiveSearchLabel".toUpperCase(), SearchOver.labels, SortOrder.asc, 0, 10);
            Assertions.assertNotNull(ires);
            Assertions.assertEquals(1, ires.getCount());
            ires = client.searchArtifacts("TESTCaseInsensitiveSEARCHLabel", SearchOver.labels, SortOrder.asc, 0, 10);
            Assertions.assertNotNull(ires);
            Assertions.assertEquals(1, ires.getCount());
        });
    }

}
