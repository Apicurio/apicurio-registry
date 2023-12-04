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

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.apicurio.registry.rest.client.models.Properties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.EditableMetaData;
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
        clientV3.groups().byGroupId(groupId).artifacts().get().get();

        String artifactId = UUID.randomUUID().toString();
        String title = "testCaseInsensitiveSearch";
        String description = "The quick brown FOX jumped over the Lazy dog.";
        String content = OPENAPI_CONTENT_TEMPLATE.replace("TITLE", title).replace("DESCRIPTION", description);

        ArtifactContent data = new ArtifactContent();
        data.setContent(content);
        clientV3.groups().byGroupId(groupId).artifacts().post(data, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.OPENAPI);
        }).get(3, TimeUnit.SECONDS);

        // Search against the name, with the exact name of the artifact
        ArtifactSearchResults results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.group = groupId;
            config.queryParameters.name = title;
            config.queryParameters.order = "asc";
            config.queryParameters.orderby = "name";
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
        }).get(3, TimeUnit.SECONDS);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());

        // Update the meta-data for the artifact
        EditableMetaData metaData = new EditableMetaData();
        metaData.setName(title);
        metaData.setDescription(description);
        metaData.setLabels(Collections.singletonList("testCaseInsensitiveSearchLabel"));
        io.apicurio.registry.rest.client.models.Properties props = new Properties();
        props.setAdditionalData(Collections.singletonMap("testCaseInsensitiveSearchKey", "testCaseInsensitiveSearchValue"));
        metaData.setProperties(props);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().put(metaData).get(3, TimeUnit.SECONDS);

        TestUtils.retry(() -> {
            // Now try various cases when searching by labels
            ArtifactSearchResults ires = clientV3.search().artifacts().get(config -> {
                config.queryParameters.group = groupId;
                config.queryParameters.order = "asc";
                config.queryParameters.orderby = "name";
                config.queryParameters.offset = 0;
                config.queryParameters.limit = 10;
                config.queryParameters.labels = new String[]{"testCaseInsensitiveSearchLabel"};
            }).get(3, TimeUnit.SECONDS);
            Assertions.assertNotNull(ires);
            Assertions.assertEquals(1, ires.getCount());
            ires = clientV3.search().artifacts().get(config -> {
                config.queryParameters.group = groupId;
                config.queryParameters.order = "asc";
                config.queryParameters.orderby = "name";
                config.queryParameters.offset = 0;
                config.queryParameters.limit = 10;
                config.queryParameters.labels = new String[]{"testCaseInsensitiveSearchLabel".toLowerCase()};
            }).get(3, TimeUnit.SECONDS);
            Assertions.assertNotNull(ires);
            Assertions.assertEquals(1, ires.getCount());
            ires = clientV3.search().artifacts().get(config -> {
                config.queryParameters.group = groupId;
                config.queryParameters.order = "asc";
                config.queryParameters.orderby = "name";
                config.queryParameters.offset = 0;
                config.queryParameters.limit = 10;
                config.queryParameters.labels = new String[]{"testCaseInsensitiveSearchLabel".toUpperCase()};
            }).get(3, TimeUnit.SECONDS);
            Assertions.assertNotNull(ires);
            Assertions.assertEquals(1, ires.getCount());
            ires = clientV3.search().artifacts().get(config -> {
                config.queryParameters.group = groupId;
                config.queryParameters.order = "asc";
                config.queryParameters.orderby = "name";
                config.queryParameters.offset = 0;
                config.queryParameters.limit = 10;
                config.queryParameters.labels = new String[]{"TESTCaseInsensitiveSEARCHLabel"};
            }).get(3, TimeUnit.SECONDS);
            Assertions.assertNotNull(ires);
            Assertions.assertEquals(1, ires.getCount());

            // Now try various cases when searching by properties and values
            ArtifactSearchResults propertiesSearch = clientV3.search().artifacts().get(config -> {
                config.queryParameters.group = groupId;
                config.queryParameters.order = "asc";
                config.queryParameters.orderby = "name";
                config.queryParameters.offset = 0;
                config.queryParameters.limit = 10;
                config.queryParameters.properties = new String[]{"testCaseInsensitiveSearchKey:testCaseInsensitiveSearchValue"};
            }).get(3, TimeUnit.SECONDS);
            Assertions.assertNotNull(propertiesSearch);
            Assertions.assertEquals(1, propertiesSearch.getCount());
            propertiesSearch = clientV3.search().artifacts().get(config -> {
                config.queryParameters.group = groupId;
                config.queryParameters.order = "asc";
                config.queryParameters.orderby = "name";
                config.queryParameters.offset = 0;
                config.queryParameters.limit = 10;
                config.queryParameters.properties = new String[]{"testCaseInsensitiveSearchKey:testCaseInsensitiveSearchValue".toLowerCase()};
            }).get(3, TimeUnit.SECONDS);
            Assertions.assertNotNull(propertiesSearch);
            Assertions.assertEquals(1, propertiesSearch.getCount());
            propertiesSearch = clientV3.search().artifacts().get(config -> {
                config.queryParameters.group = groupId;
                config.queryParameters.order = "asc";
                config.queryParameters.orderby = "name";
                config.queryParameters.offset = 0;
                config.queryParameters.limit = 10;
                config.queryParameters.properties = new String[]{"testCaseInsensitiveSearchKey:testCaseInsensitiveSearchValue".toUpperCase()};
            }).get(3, TimeUnit.SECONDS);
            Assertions.assertNotNull(propertiesSearch);
            Assertions.assertEquals(1, propertiesSearch.getCount());
            propertiesSearch = clientV3.search().artifacts().get(config -> {
                config.queryParameters.group = groupId;
                config.queryParameters.order = "asc";
                config.queryParameters.orderby = "name";
                config.queryParameters.offset = 0;
                config.queryParameters.limit = 10;
                config.queryParameters.properties = new String[]{"TESTCaseInsensitiveSEARCHKey:TESTCaseInsensitiveSearchVALUE".toUpperCase()};
            }).get(3, TimeUnit.SECONDS);
            Assertions.assertNotNull(propertiesSearch);
            Assertions.assertEquals(1, propertiesSearch.getCount());

            // Now try various cases when searching by properties
            ArtifactSearchResults propertiesKeySearch = clientV3.search().artifacts().get(config -> {
                config.queryParameters.group = groupId;
                config.queryParameters.order = "asc";
                config.queryParameters.orderby = "name";
                config.queryParameters.offset = 0;
                config.queryParameters.limit = 10;
                config.queryParameters.properties = new String[]{"testCaseInsensitiveSearchKey"};
            }).get(3, TimeUnit.SECONDS);
            Assertions.assertNotNull(propertiesKeySearch);
            Assertions.assertEquals(1, propertiesKeySearch.getCount());
            propertiesKeySearch = clientV3.search().artifacts().get(config -> {
                config.queryParameters.group = groupId;
                config.queryParameters.order = "asc";
                config.queryParameters.orderby = "name";
                config.queryParameters.offset = 0;
                config.queryParameters.limit = 10;
                config.queryParameters.properties = new String[]{"testCaseInsensitiveSearchKey".toLowerCase()};
            }).get(3, TimeUnit.SECONDS);
            Assertions.assertNotNull(propertiesKeySearch);
            Assertions.assertEquals(1, propertiesKeySearch.getCount());
            propertiesKeySearch = clientV3.search().artifacts().get(config -> {
                config.queryParameters.group = groupId;
                config.queryParameters.order = "asc";
                config.queryParameters.orderby = "name";
                config.queryParameters.offset = 0;
                config.queryParameters.limit = 10;
                config.queryParameters.properties = new String[]{"testCaseInsensitiveSearchKey".toUpperCase()};
            }).get(3, TimeUnit.SECONDS);
            Assertions.assertNotNull(propertiesKeySearch);
            Assertions.assertEquals(1, propertiesKeySearch.getCount());
            propertiesKeySearch = clientV3.search().artifacts().get(config -> {
                config.queryParameters.group = groupId;
                config.queryParameters.order = "asc";
                config.queryParameters.orderby = "name";
                config.queryParameters.offset = 0;
                config.queryParameters.limit = 10;
                config.queryParameters.properties = new String[]{"TESTCaseInsensitiveSEARCHKey"};
            }).get(3, TimeUnit.SECONDS);
            Assertions.assertNotNull(propertiesKeySearch);
            Assertions.assertEquals(1, propertiesSearch.getCount());
        });
    }

}
