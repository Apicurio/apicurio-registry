package io.apicurio.registry.noprofile.references;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEREFERENCE on a reference graph that cannot be fully inlined (a missing target, or a cycle) must fail
 * with a 4xx naming the problem, never return partial content or a 500 (#11059, #11030), and the
 * NO_CIRCULAR_REFERENCES integrity rule must reject a version that closes a cycle (#11030).
 */
@QuarkusTest
public class DereferenceIncompleteGraphTest extends AbstractResourceTestBase {

    private static final String AVRO_ORDER = """
            {"type": "record", "name": "Order", "namespace": "com.example",
             "fields": [{"name": "total", "type": "com.example.common.Money"}]}
            """;

    private static final String JSON_ORDER = """
            {"$schema": "http://json-schema.org/draft-07/schema#", "type": "object",
             "properties": {"total": {"$ref": "money.json"}}}
            """;

    @Test
    public void dereferenceAvroWithMissingReferenceFails() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createArtifactWithReferences(groupId, "order", ArtifactType.AVRO, AVRO_ORDER, ContentTypes.APPLICATION_JSON,
                List.of(ref(groupId, "money", "com.example.common.Money")));

        Response response = dereference(groupId, "order");

        assertEquals(422, response.getStatusCode(), response.asString());
        assertTrue(response.asString().contains(
                "these references point to versions that do not exist: 'com.example.common.Money' ("
                        + groupId + "/money@1)"), response.asString());
    }

    @Test
    public void dereferenceJsonSchemaWithMissingReferenceFails() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createArtifactWithReferences(groupId, "order", ArtifactType.JSON, JSON_ORDER, ContentTypes.APPLICATION_JSON,
                List.of(ref(groupId, "money", "money.json")));

        Response response = dereference(groupId, "order");

        assertEquals(422, response.getStatusCode(), response.asString());
        assertTrue(response.asString().contains("'money.json' (" + groupId + "/money@1)"), response.asString());
    }

    @Test
    public void dereferenceCyclicGraphFails() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createArtifactWithReferences(groupId, "a", ArtifactType.JSON, jsonRef("b.json"), ContentTypes.APPLICATION_JSON,
                List.of(ref(groupId, "b", "b.json")));
        createArtifactWithReferences(groupId, "b", ArtifactType.JSON, jsonRef("a.json"), ContentTypes.APPLICATION_JSON,
                List.of(ref(groupId, "a", "a.json")));

        Response response = dereference(groupId, "a");

        assertEquals(422, response.getStatusCode(), response.asString());
        assertTrue(response.asString().contains("circular reference " + groupId + "/b@1 -> " + groupId + "/a@1 -> "
                + groupId + "/b@1"), response.asString());
    }

    @Test
    public void integrityRuleRejectsVersionClosingACycle() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createArtifactWithReferences(groupId, "a", ArtifactType.JSON, jsonRef("b.json"), ContentTypes.APPLICATION_JSON,
                List.of(ref(groupId, "b", "b.json")));
        createGroupRule(groupId, RuleType.INTEGRITY, "NO_CIRCULAR_REFERENCES");

        RuleViolationProblemDetails error = assertThrows(RuleViolationProblemDetails.class,
                () -> createArtifactWithReferences(groupId, "b", ArtifactType.JSON, jsonRef("a.json"),
                        ContentTypes.APPLICATION_JSON, List.of(ref(groupId, "a", "a.json"))));

        assertEquals("RuleViolationException", error.getName());
        assertEquals("Circular artifact reference(s) detected.", error.getTitle());
        // The existing rule-violation status: 400, or 409 with legacy error codes enabled.
        assertEquals(400, error.getStatus());
        assertEquals("a.json", error.getCauses().get(0).getContext());
    }

    @Test
    public void integrityRuleAcceptsReferenceToAnotherMissingVersionOfTheArtifactBeingCreated() throws Exception {
        String groupId = TestUtils.generateGroupId();
        // a@1 references b@2, which does not exist; b@1 is created below and is not b@2
        createArtifactWithReferences(groupId, "a", ArtifactType.JSON, jsonRef("b.json"), ContentTypes.APPLICATION_JSON,
                List.of(ArtifactReference.builder().groupId(groupId).artifactId("b").version("2").name("b.json")
                        .build()));
        createGroupRule(groupId, RuleType.INTEGRITY, "NO_CIRCULAR_REFERENCES");

        var created = createArtifactWithReferences(groupId, "b", ArtifactType.JSON, jsonRef("a.json"),
                ContentTypes.APPLICATION_JSON, List.of(ref(groupId, "a", "a.json")));

        assertEquals("1", created.getVersion().getVersion());
    }

    @Test
    public void integrityRuleAcceptsReferenceToAnExistingVersionOfTheSameArtifact() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createArtifactWithReferences(groupId, "a", ArtifactType.JSON, "{\"type\": \"object\"}",
                ContentTypes.APPLICATION_JSON, List.of());
        createGroupRule(groupId, RuleType.INTEGRITY, "NO_CIRCULAR_REFERENCES");

        CreateVersion createVersion = TestUtils.clientCreateVersion(jsonRef("a1.json"), ContentTypes.APPLICATION_JSON);
        createVersion.setVersion("2");
        var reference = new io.apicurio.registry.rest.client.models.ArtifactReference();
        reference.setGroupId(groupId);
        reference.setArtifactId("a");
        reference.setVersion("1");
        reference.setName("a1.json");
        createVersion.getContent().setReferences(List.of(reference));
        VersionMetaData created = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId("a").versions()
                .post(createVersion);

        assertEquals("2", created.getVersion());
    }

    @Test
    public void integrityRuleAcceptsReferenceToEarlierVersionOfAnotherArtifact() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createArtifactWithReferences(groupId, "money", ArtifactType.JSON, "{\"type\": \"number\"}",
                ContentTypes.APPLICATION_JSON, List.of());
        createGroupRule(groupId, RuleType.INTEGRITY, "NO_CIRCULAR_REFERENCES");

        var created = createArtifactWithReferences(groupId, "order", ArtifactType.JSON, JSON_ORDER,
                ContentTypes.APPLICATION_JSON, List.of(ref(groupId, "money", "money.json")));

        assertEquals("1", created.getVersion().getVersion());
    }

    private Response dereference(String groupId, String artifactId) {
        return given().when().pathParam("groupId", groupId).pathParam("artifactId", artifactId)
                .queryParam("references", "DEREFERENCE")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/1/content");
    }

    private static ArtifactReference ref(String groupId, String artifactId, String name) {
        return ArtifactReference.builder().groupId(groupId).artifactId(artifactId).version("1").name(name).build();
    }

    private static String jsonRef(String name) {
        return "{\"type\": \"object\", \"properties\": {\"next\": {\"$ref\": \"" + name + "\"}}}";
    }
}
