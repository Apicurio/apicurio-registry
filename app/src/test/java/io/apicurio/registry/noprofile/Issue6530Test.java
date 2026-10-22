package io.apicurio.registry.noprofile;

import io.apicurio.registry.AbstractResourceTestBase;

import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Test for GitHub issue #6530: Registering protobuf schemas with references results in error when Validity rule is enabled.
 * 
 * This test reproduces the bug where attempting to register a protobuf schema that imports/references another 
 * protobuf schema fails with a RuleViolationException when the Validity rule is enabled at FULL level.
 * 
 * The test scenario:
 * 1. Creates a test group with FULL Validity rule enabled
 * 2. Registers a base protobuf schema (UserCreated.proto) - should succeed
 * 3. Registers a second protobuf schema (UserCreatedEvent.proto) that imports the first schema - should succeed but currently fails
 * 
 * Expected behavior: Both schemas should register successfully
 * Actual behavior: The second schema registration fails with RuleViolationException: "Syntax violation for Protobuf artifact."
 * 
 * @see <a href="https://github.com/Apicurio/apicurio-registry/issues/6530">GitHub Issue #6530</a>
 */
@QuarkusTest
public class Issue6530Test extends AbstractResourceTestBase {

    private static final String USER_CREATED_PROTO = """
syntax = "proto3";
package demo;

message UserCreated {
  string user_id = 1;
  string name = 2;
  int32 age = 3;
  string email = 4;
}
""";

    private static final String USER_CREATED_EVENT_PROTO = """
syntax = "proto3";
package demo;

import "demo/UserCreated.proto";

message UserCreatedEvent {
  UserCreated user = 1;
  string source = 2;
  int64 timestamp = 3;
}
""";

    private static final String USER_CREATED_PROTO_INVALID = """
syntax = "proto3";
package demo;

message UserCreated {
  string user_id = 1;
  string name = 2;
  int32 age = 3;
  string email = 4;
}
""";

    private static final String USER_CREATED_EVENT_PROTO_INVALID = """
syntax = "proto3";
package demo;

import "UserCreated.proto";

message UserCreatedEvent {
  UserCreated user = 1;
  string source = 2;
  int64 timestamp = 3;
}
""";

    @Test
    public void testIssue6530() throws Exception {
        runTest(USER_CREATED_PROTO, USER_CREATED_EVENT_PROTO, "demo/UserCreated.proto");
    }

    @Test
    public void testIssue6530_Invalid() throws Exception {
        runTest(USER_CREATED_PROTO_INVALID, USER_CREATED_EVENT_PROTO_INVALID, "UserCreated.proto");
    }

    private void runTest(String userCreatedContent, String userCreatedEventContent, String refName) throws Exception {
        String groupId = TestUtils.generateGroupId();
        String referencedArtifactId = "UserCreated.proto";
        String referencingArtifactId = "UserCreatedEvent.proto";

        // Create a group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Enable FULL Validity rule at the Group level
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).rules().post(createRule);

        // Enable FULL Integrity rule at the Group level
        createRule = new CreateRule();
        createRule.setRuleType(RuleType.INTEGRITY);
        createRule.setConfig(IntegrityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).rules().post(createRule);

        // Create the first artifact (UserCreated.proto) - this should succeed
        CreateArtifactResponse response1 = createArtifact(groupId, referencedArtifactId,
                ArtifactType.PROTOBUF, userCreatedContent, ContentTypes.APPLICATION_PROTOBUF);

        Assertions.assertNotNull(response1);
        Assertions.assertEquals(groupId, response1.getArtifact().getGroupId());
        Assertions.assertEquals(referencedArtifactId, response1.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.PROTOBUF, response1.getArtifact().getArtifactType());
        Assertions.assertEquals("1", response1.getVersion().getVersion());

        // Create reference to the first artifact
        ArtifactReference reference = ArtifactReference.builder()
                .groupId(groupId)
                .artifactId(referencedArtifactId)
                .version("1")
                .name(refName)
                .build();

        // Create the second artifact (UserCreatedEvent.proto) with reference
        CreateArtifactResponse response2 = createArtifactWithReferences(groupId, referencingArtifactId,
                ArtifactType.PROTOBUF, userCreatedEventContent, ContentTypes.APPLICATION_PROTOBUF,
                List.of(reference));

        Assertions.assertNotNull(response2);
        Assertions.assertEquals(groupId, response2.getArtifact().getGroupId());
        Assertions.assertEquals(referencingArtifactId, response2.getArtifact().getArtifactId());
        Assertions.assertEquals(ArtifactType.PROTOBUF, response2.getArtifact().getArtifactType());
        Assertions.assertEquals("1", response2.getVersion().getVersion());

        // Verify the references are properly stored
        var actualReferences = clientV3.groups().byGroupId(groupId)
                .artifacts().byArtifactId(referencingArtifactId)
                .versions().byVersionExpression("1")
                .references().get();

        Assertions.assertEquals(1, actualReferences.size());
        Assertions.assertEquals(reference.getName(), actualReferences.get(0).getName());
        Assertions.assertEquals(reference.getVersion(), actualReferences.get(0).getVersion());
        Assertions.assertEquals(reference.getArtifactId(), actualReferences.get(0).getArtifactId());
        Assertions.assertEquals(reference.getGroupId(), actualReferences.get(0).getGroupId());
    }
}
