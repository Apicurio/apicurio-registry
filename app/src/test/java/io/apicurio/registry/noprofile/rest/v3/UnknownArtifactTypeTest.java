package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Creating an artifact with an explicit but unknown artifact type is a client error. It used to fail
 * with a 500 because the type's provider was looked up before the type was validated.
 */
@QuarkusTest
public class UnknownArtifactTypeTest extends AbstractResourceTestBase {

    @Test
    public void testCreateArtifactWithUnknownTypeIsBadRequest() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, "GARBAGE",
                "{\"type\": \"string\"}", ContentTypes.APPLICATION_JSON);

        RuleViolationProblemDetails error = Assertions.assertThrows(RuleViolationProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact));

        Assertions.assertEquals(400, error.getStatus());
        Assertions.assertEquals("InvalidArtifactTypeException", error.getName());
        Assertions.assertEquals("Invalid or unknown artifact type: GARBAGE", error.getTitle());
    }
}
