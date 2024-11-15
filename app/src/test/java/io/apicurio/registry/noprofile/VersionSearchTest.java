package io.apicurio.registry.noprofile;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class VersionSearchTest extends AbstractResourceTestBase {

    @Test
    void testFilterByArtifactType() throws Exception {
        String groupId = TestUtils.generateGroupId();

        createArtifact(groupId, "avro-artifact", ArtifactType.AVRO, "{}", ContentTypes.APPLICATION_JSON);
        createArtifactVersion(groupId, "avro-artifact", "{ }", ContentTypes.APPLICATION_JSON);
        createArtifact(groupId, "json-artifact", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);

        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(3, results.getCount());

        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.artifactType = ArtifactType.AVRO;
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(2, results.getCount());

        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.artifactType = ArtifactType.JSON;
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());

    }
}