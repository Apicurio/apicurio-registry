package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class VersionModifiedByOnTest extends AbstractResourceTestBase {

    @Test
    public void testSearchVersionsWithModified() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String groupId = TestUtils.generateGroupId();

        // Create 5 artifacts
        for (int idx = 0; idx < 5; idx++) {
            String artifactId = TestUtils.generateArtifactId();
            createArtifact(groupId, artifactId, ArtifactType.OPENAPI, artifactContent,
                    ContentTypes.APPLICATION_JSON);
        }

        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
        });
        Assertions.assertEquals(5, results.getCount());
        for (SearchedVersion version : results.getVersions()) {
            Assertions.assertNotNull(version.getModifiedOn());
        }
    }

    @Test
    public void testVersionMetaDataWithModified() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON, (ca) -> {
                    ca.getFirstVersion().setVersion("1.0");
                });

        VersionMetaData vmd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("1.0").get();
        Assertions.assertNotNull(vmd);
        Assertions.assertNotNull(vmd.getModifiedOn());
    }

}
