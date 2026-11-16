package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class SqlEventOrderTest extends AbstractResourceTestBase {

    @Inject
    SqlOutboxEventRecorder recorder;

    @Test
    public void artifactCreatedIsFiredBeforeItsFirstVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact("orders", ArtifactType.JSON,
                "{\"type\": \"object\"}", ContentTypes.APPLICATION_JSON);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        assertEquals(List.of("GROUP_CREATED", "ARTIFACT_CREATED", "ARTIFACT_VERSION_CREATED"),
                recorder.typesForGroup(groupId));
    }
}
