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

package io.apicurio.registry.noprofile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import io.apicurio.registry.AbstractResourceTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.rest.v2.beans.SortBy;
import io.apicurio.registry.rest.v2.beans.SortOrder;
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

    @Test
    void testCaseInsensitiveSearch() throws Exception {
        String groupId = "ArtifactSearchTest_testCaseInsensitiveSearch";
        // warm-up
        clientV2.listArtifactsInGroup(groupId);

        String artifactId = UUID.randomUUID().toString();
        String title = "testCaseInsensitiveSearch";
        String description = "The quick brown FOX jumped over the Lazy dog.";
        String content = OPENAPI_CONTENT_TEMPLATE.replace("TITLE", title).replace("DESCRIPTION", description);
        ByteArrayInputStream artifactData = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        ArtifactMetaData amd = clientV2.createArtifact(groupId, artifactId, ArtifactType.OPENAPI, artifactData);
        long id = amd.getGlobalId();

        this.waitForGlobalId(id);

        // Search against the name, with the exact name of the artifact
        ArtifactSearchResults results = clientV2.searchArtifacts(groupId, title, null, null, null, SortBy.name, SortOrder.asc, 0, 10);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());

        // Update the meta-data for the artifact
        EditableMetaData metaData = new EditableMetaData();
        metaData.setName(title);
        metaData.setDescription(description);
        metaData.setLabels(Collections.singletonList("testCaseInsensitiveSearchLabel"));
        metaData.setProperties(Collections.singletonMap("testCaseInsensitiveSearchKey", "testCaseInsensitiveSearchValue"));
        clientV2.updateArtifactMetaData(groupId, artifactId, metaData);

        TestUtils.retry(() -> {
            // Now try various cases when searching by labels
            ArtifactSearchResults ires = clientV2.searchArtifacts(groupId, null, null, List.of("testCaseInsensitiveSearchLabel"), null, SortBy.name, SortOrder.asc, 0, 10);
            Assertions.assertNotNull(ires);
            Assertions.assertEquals(1, ires.getCount());
            ires = clientV2.searchArtifacts(groupId, null, null, List.of("testCaseInsensitiveSearchLabel".toLowerCase()), null, SortBy.name, SortOrder.asc, 0, 10);
            Assertions.assertNotNull(ires);
            Assertions.assertEquals(1, ires.getCount());
            ires = clientV2.searchArtifacts(groupId, null, null, List.of("testCaseInsensitiveSearchLabel".toUpperCase()), null, SortBy.name, SortOrder.asc, 0, 10);
            Assertions.assertNotNull(ires);
            Assertions.assertEquals(1, ires.getCount());
            ires = clientV2.searchArtifacts(groupId, null, null, List.of("TESTCaseInsensitiveSEARCHLabel"), null, SortBy.name, SortOrder.asc, 0, 10);
            Assertions.assertNotNull(ires);
            Assertions.assertEquals(1, ires.getCount());

            // Now try various cases when searching by properties and values
            ArtifactSearchResults propertiesSearch = clientV2.searchArtifacts(groupId, null, null, null, List.of("testCaseInsensitiveSearchKey:testCaseInsensitiveSearchValue"), SortBy.name, SortOrder.asc, 0, 10);
            Assertions.assertNotNull(propertiesSearch);
            Assertions.assertEquals(1, propertiesSearch.getCount());
            propertiesSearch = clientV2.searchArtifacts(groupId, null, null, null, List.of("testCaseInsensitiveSearchKey:testCaseInsensitiveSearchValue".toLowerCase()), SortBy.name, SortOrder.asc, 0, 10);
            Assertions.assertNotNull(propertiesSearch);
            Assertions.assertEquals(1, propertiesSearch.getCount());
            propertiesSearch = clientV2.searchArtifacts(groupId, null, null, null,  List.of("testCaseInsensitiveSearchKey:testCaseInsensitiveSearchValue".toUpperCase()), SortBy.name, SortOrder.asc, 0, 10);
            Assertions.assertNotNull(propertiesSearch);
            Assertions.assertEquals(1, propertiesSearch.getCount());
            propertiesSearch = clientV2.searchArtifacts(groupId, null, null, null, List.of("TESTCaseInsensitiveSEARCHKey:TESTCaseInsensitiveSearchVALUE"), SortBy.name, SortOrder.asc, 0, 10);
            Assertions.assertNotNull(propertiesSearch);
            Assertions.assertEquals(1, propertiesSearch.getCount());

            // Now try various cases when searching by properties
            ArtifactSearchResults propertiesKeySearch = clientV2.searchArtifacts(groupId, null, null, null, List.of("testCaseInsensitiveSearchKey"), SortBy.name, SortOrder.asc, 0, 10);
            Assertions.assertNotNull(propertiesKeySearch);
            Assertions.assertEquals(1, propertiesKeySearch.getCount());
            propertiesKeySearch = clientV2.searchArtifacts(groupId, null, null, null, List.of("testCaseInsensitiveSearchKey".toLowerCase()), SortBy.name, SortOrder.asc, 0, 10);
            Assertions.assertNotNull(propertiesKeySearch);
            Assertions.assertEquals(1, propertiesKeySearch.getCount());
            propertiesKeySearch = clientV2.searchArtifacts(groupId, null, null, null,  List.of("testCaseInsensitiveSearchKey".toUpperCase()), SortBy.name, SortOrder.asc, 0, 10);
            Assertions.assertNotNull(propertiesKeySearch);
            Assertions.assertEquals(1, propertiesKeySearch.getCount());
            propertiesKeySearch = clientV2.searchArtifacts(groupId, null, null, null, List.of("TESTCaseInsensitiveSEARCHKey"), SortBy.name, SortOrder.asc, 0, 10);
            Assertions.assertNotNull(propertiesKeySearch);
            Assertions.assertEquals(1, propertiesSearch.getCount());
        });
    }

}
