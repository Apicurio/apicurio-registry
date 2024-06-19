package io.apicurio.registry.noprofile;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.ArtifactSortBy;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

@QuarkusTest
public class ArtifactSearchTest extends AbstractResourceTestBase {

    private static final String OPENAPI_CONTENT_TEMPLATE = "{\r\n" + "    \"openapi\": \"3.0.2\",\r\n"
            + "    \"info\": {\r\n" + "        \"title\": \"TITLE\",\r\n"
            + "        \"version\": \"1.0.0\",\r\n" + "        \"description\": \"DESCRIPTION\"\r\n"
            + "    }\r\n" + "}";

    @Test
    void testCaseInsensitiveSearch() throws Exception {
        String groupId = "ArtifactSearchTest_testCaseInsensitiveSearch";
        // warm-up
        clientV3.groups().byGroupId(groupId).artifacts().get();

        String artifactId = UUID.randomUUID().toString();
        String title = "testCaseInsensitiveSearch";
        String description = "The quick brown FOX jumped over the Lazy dog.";
        String content = OPENAPI_CONTENT_TEMPLATE.replace("TITLE", title).replace("DESCRIPTION", description);

        createArtifact(groupId, artifactId, ArtifactType.OPENAPI, content, ContentTypes.APPLICATION_JSON,
                (createArtifact) -> {
                    createArtifact.setName(title);
                    createArtifact.setDescription(description);
                    createArtifact.getFirstVersion().setName(title);
                    createArtifact.getFirstVersion().setDescription(description);
                });

        // Search against the name, with the exact name of the artifact
        ArtifactSearchResults results = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.name = title;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());

        // Update the meta-data for the artifact
        EditableArtifactMetaData metaData = new EditableArtifactMetaData();
        metaData.setName(title);
        metaData.setDescription(description);
        Labels labels = new Labels();
        labels.setAdditionalData(
                Collections.singletonMap("testCaseInsensitiveSearchKey", "testCaseInsensitiveSearchValue"));
        metaData.setLabels(labels);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(metaData);

        // Now try various cases when searching by labels
        ArtifactSearchResults ires = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] { "testCaseInsensitiveSearchKey" };
        });
        Assertions.assertNotNull(ires);
        Assertions.assertEquals(1, ires.getCount());
        ires = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] { "testCaseInsensitiveSearchKey".toLowerCase() };
        });
        Assertions.assertNotNull(ires);
        Assertions.assertEquals(1, ires.getCount());
        ires = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] { "testCaseInsensitiveSearchKey".toUpperCase() };
        });
        Assertions.assertNotNull(ires);
        Assertions.assertEquals(1, ires.getCount());
        ires = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] { "TESTCaseInsensitiveSEARCHKey" };
        });
        Assertions.assertNotNull(ires);
        Assertions.assertEquals(1, ires.getCount());

        // Now try various cases when searching by properties and values
        ArtifactSearchResults propertiesSearch = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] {
                    "testCaseInsensitiveSearchKey:testCaseInsensitiveSearchValue" };
        });
        Assertions.assertNotNull(propertiesSearch);
        Assertions.assertEquals(1, propertiesSearch.getCount());
        propertiesSearch = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] {
                    "testCaseInsensitiveSearchKey:testCaseInsensitiveSearchValue".toLowerCase() };
        });
        Assertions.assertNotNull(propertiesSearch);
        Assertions.assertEquals(1, propertiesSearch.getCount());
        propertiesSearch = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] {
                    "testCaseInsensitiveSearchKey:testCaseInsensitiveSearchValue".toUpperCase() };
        });
        Assertions.assertNotNull(propertiesSearch);
        Assertions.assertEquals(1, propertiesSearch.getCount());
        propertiesSearch = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] {
                    "TESTCaseInsensitiveSEARCHKey:TESTCaseInsensitiveSearchVALUE".toUpperCase() };
        });
        Assertions.assertNotNull(propertiesSearch);
        Assertions.assertEquals(1, propertiesSearch.getCount());

        // Now try various cases when searching by properties
        ArtifactSearchResults propertiesKeySearch = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] { "testCaseInsensitiveSearchKey" };
        });
        Assertions.assertNotNull(propertiesKeySearch);
        Assertions.assertEquals(1, propertiesKeySearch.getCount());
        propertiesKeySearch = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] { "testCaseInsensitiveSearchKey".toLowerCase() };
        });
        Assertions.assertNotNull(propertiesKeySearch);
        Assertions.assertEquals(1, propertiesKeySearch.getCount());
        propertiesKeySearch = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] { "testCaseInsensitiveSearchKey".toUpperCase() };
        });
        Assertions.assertNotNull(propertiesKeySearch);
        Assertions.assertEquals(1, propertiesKeySearch.getCount());
        propertiesKeySearch = clientV3.search().artifacts().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.order = SortOrder.Asc;
            config.queryParameters.orderby = ArtifactSortBy.Name;
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 10;
            config.queryParameters.labels = new String[] { "TESTCaseInsensitiveSEARCHKey" };
        });
        Assertions.assertNotNull(propertiesKeySearch);
        Assertions.assertEquals(1, propertiesSearch.getCount());
    }

}
