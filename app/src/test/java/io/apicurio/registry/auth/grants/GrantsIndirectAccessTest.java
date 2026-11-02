package io.apicurio.registry.auth.grants;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Per-resource authorization of resources reached indirectly: content loaded through references,
 * artifacts written by write hooks, groups receiving artifacts created through the ccompat, MCP
 * and Iceberg APIs, and referrers listed by the ccompat API.
 *
 * <p>Owner-only authorization and the owner bypass are disabled, so the grants alone decide.</p>
 */
@QuarkusTest
@TestProfile(GrantsIndirectAccessTest.IndirectAccessProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class GrantsIndirectAccessTest extends AbstractResourceTestBase {

    private static final String GROUP = "indirect";
    private static final String ICEBERG_SOURCE = "icesrc";
    private static final String LOCKED_GROUP = "lockedgrp";
    private static final String LOCKED_MCP_NAMESPACE = "io.github.lockedgrp";
    private static final String OPEN_MCP_NAMESPACE = "io.github.opengrp";
    private static final String GENERATED_LOCKED_GROUP = "lockedgen";

    private static final String RULES = """
            from io.apicurio.authz import Artifact, Group;
            deny User with name = "bob" to * Artifact with name like "%1$s/private-*";
            deny User with name = "bob" to WRITE Artifact with name in {"%1$s/denied-model-input-schema",
                "%1$s/locked-model-input-schema", "%1$s/denied-api-agent-card"};
            deny User with name = "bob" to WRITE Group with name in {"%3$s", "%4$s", "%6$s"};
            allow User with name = "bob" to WRITE Group with name in {"%1$s", "%2$s", "%5$s"};
            allow User with name = "bob" to WRITE Artifact with name like "%1$s/*";
            allow User with name = "bob" to WRITE Artifact with name like "%2$s/*";
            allow User with name = "bob" to WRITE Artifact with name like "%3$s/*";
            allow User with name = "bob" to WRITE Artifact with name like "%4$s/*";
            allow User with name = "bob" to WRITE Artifact with name like "%5$s/*";
            allow User with name = "bob" to WRITE Artifact with name like "%6$s/*";
            allow User with name = "bob" to READ Artifact with name = "default/ccompat-open-target";
            otherwise deny;
            """.formatted(GROUP, ICEBERG_SOURCE, LOCKED_GROUP, LOCKED_MCP_NAMESPACE, OPEN_MCP_NAMESPACE,
                    GENERATED_LOCKED_GROUP);

    static final Path GRANTS = createGrantsFile();

    private static Path createGrantsFile() {
        try {
            Path file = Files.createTempFile("grants-indirect-access", ".acl");
            Files.writeString(file, RULES);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class IndirectAccessProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> map = new HashMap<>();
            map.put("quarkus.oidc.tenant-enabled", "false");
            map.put("quarkus.http.auth.basic", "true");
            map.put("apicurio.auth.role-based-authorization", "true");
            map.put("quarkus.security.users.embedded.enabled", "true");
            map.put("quarkus.security.users.embedded.plain-text", "true");
            map.put("quarkus.security.users.embedded.users.alice", "alice");
            map.put("quarkus.security.users.embedded.users.bob", "bob");
            map.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
            map.put("quarkus.security.users.embedded.roles.bob", "sr-developer");
            map.put("apicurio.auth.resource-based-authorization.enabled", "true");
            map.put("apicurio.auth.resource-based-authorization.rules.path", GRANTS.toString());
            map.put("apicurio.auth.resource-based-authorization.rules.reload-enabled", "false");
            map.put("apicurio.auth.resource-based-authorization.owner-bypass.enabled", "false");
            map.put("apicurio.features.experimental.enabled", "true");
            map.put("apicurio.a2a.enabled", "true");
            map.put("apicurio.a2a.openapi-integration.enabled", "true");
            map.put("apicurio.iceberg.enabled", "true");
            map.put("apicurio.mcp-registry.enabled", "true");
            map.put("apicurio.rest.mutability.artifact-version-content.enabled", "true");
            return map;
        }
    }

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl).vertx(vertx).basicAuth("alice", "alice"));
    }

    private static RequestSpecification alice() {
        return given().auth().preemptive().basic("alice", "alice");
    }

    private static RequestSpecification bob() {
        return given().auth().preemptive().basic("bob", "bob");
    }

    private static String path(String group, String artifactId) {
        return "/registry/v3/groups/" + group + "/artifacts/" + artifactId;
    }

    private static Map<String, Object> reference(String artifactId, String name) {
        return Map.of("groupId", GROUP, "artifactId", artifactId, "version", "1", "name", name);
    }

    private static Map<String, Object> createBody(String artifactId, String type, String content,
            List<Map<String, Object>> references) {
        return Map.of("artifactId", artifactId, "artifactType", type, "firstVersion",
                Map.of("version", "1", "content", Map.of("content", content, "contentType", CT_JSON,
                        "references", references)));
    }

    private static void create(RequestSpecification as, String artifactId, String type, String content,
            List<Map<String, Object>> references, int expectedStatus) {
        as.contentType(CT_JSON).body(createBody(artifactId, type, content, references))
                .post("/registry/v3/groups/" + GROUP + "/artifacts").then().statusCode(expectedStatus);
    }

    private static long versionMetaData(String artifactId, String field) {
        return alice().get(path(GROUP, artifactId) + "/versions/1").then().statusCode(200)
                .extract().jsonPath().getLong(field);
    }

    private static int versionCount(String group, String artifactId) {
        return alice().get(path(group, artifactId) + "/versions").then().statusCode(200)
                .extract().path("count");
    }

    // ==================== References ====================

    private static final String PRIVATE_AVRO = """
            {"type":"record","name":"Secret","doc":"CONFIDENTIAL-MARKER",\
            "fields":[{"name":"salary","type":"string"}]}""";

    private static final String OPEN_AVRO = """
            {"type":"record","name":"Open","fields":[{"name":"visible","type":"string"}]}""";

    @Test
    public void dereferencingRequiresReadOnEveryReferencedArtifact() {
        create(alice(), "private-avro", "AVRO", PRIVATE_AVRO, List.of(), 200);
        create(alice(), "public-wrapper", "AVRO",
                "{\"type\":\"record\",\"name\":\"Public\",\"fields\":[{\"name\":\"secret\",\"type\":\"Secret\"}]}",
                List.of(reference("private-avro", "Secret")), 200);
        long globalId = versionMetaData("public-wrapper", "globalId");
        long contentId = versionMetaData("public-wrapper", "contentId");
        String content = path(GROUP, "public-wrapper") + "/versions/1/content";

        bob().get(path(GROUP, "private-avro") + "/versions/1/content").then().statusCode(403);
        // The wrapper itself is readable, as long as its references are not resolved
        bob().get(content).then().statusCode(200).body(not(containsString("CONFIDENTIAL-MARKER")));

        bob().get(content + "?references=DEREFERENCE").then().statusCode(403);
        bob().get(content + "?canonical=true").then().statusCode(403);
        bob().get("/registry/v3/ids/globalIds/" + globalId + "?references=DEREFERENCE").then().statusCode(403);
        bob().get("/registry/v2/groups/" + GROUP + "/artifacts/public-wrapper?dereference=true")
                .then().statusCode(403);
        bob().get("/ccompat/v7/schemas/ids/" + contentId + "?format=resolved").then().statusCode(403);
        bob().get("/ccompat/v7/schemas/ids/" + contentId).then().statusCode(200)
                .body("schema", not(containsString("CONFIDENTIAL-MARKER")));

        // The admin can dereference it: the denial comes from the grants, not from the content
        alice().get(content + "?references=DEREFERENCE").then().statusCode(200)
                .body(containsString("CONFIDENTIAL-MARKER"));
    }

    @Test
    public void dereferencingChecksTransitiveReferences() {
        create(alice(), "private-deep", "AVRO", PRIVATE_AVRO.replace("Secret", "Deep"), List.of(), 200);
        create(alice(), "middle", "AVRO",
                "{\"type\":\"record\",\"name\":\"Middle\",\"fields\":[{\"name\":\"d\",\"type\":\"Deep\"}]}",
                List.of(reference("private-deep", "Deep")), 200);
        create(alice(), "outer", "AVRO",
                "{\"type\":\"record\",\"name\":\"Outer\",\"fields\":[{\"name\":\"m\",\"type\":\"Middle\"}]}",
                List.of(reference("middle", "Middle")), 200);

        // middle is readable, but resolving it loads private-deep
        bob().get(path(GROUP, "middle") + "/versions/1/content").then().statusCode(200);
        bob().get(path(GROUP, "outer") + "/versions/1/content").then().statusCode(200);
        bob().get(path(GROUP, "middle") + "/versions/1/content?references=DEREFERENCE").then().statusCode(403);
        bob().get(path(GROUP, "outer") + "/versions/1/content?references=DEREFERENCE").then().statusCode(403);
    }

    @Test
    public void readableReferencesAreDereferenced() {
        create(alice(), "open-avro", "AVRO", OPEN_AVRO, List.of(), 200);
        create(bob(), "open-wrapper", "AVRO",
                "{\"type\":\"record\",\"name\":\"Wrapper\",\"fields\":[{\"name\":\"o\",\"type\":\"Open\"}]}",
                List.of(reference("open-avro", "Open")), 200);

        bob().get(path(GROUP, "open-wrapper") + "/versions/1/content?references=DEREFERENCE")
                .then().statusCode(200).body(containsString("visible"));
    }

    @Test
    public void registeringContentRequiresReadOnItsReferences() {
        create(alice(), "private-target", "AVRO", PRIVATE_AVRO, List.of(), 200);

        create(bob(), "bob-wrapper", "AVRO",
                "{\"type\":\"record\",\"name\":\"Public\",\"fields\":[{\"name\":\"secret\",\"type\":\"Secret\"}]}",
                List.of(reference("private-target", "Secret")), 403);
        alice().get(path(GROUP, "bob-wrapper")).then().statusCode(404);

        // ccompat resolves references in the default group. bob may read only ccompat-open-target there,
        // so the two registrations below differ only in the referenced artifact
        for (String target : List.of("ccompat-private-target", "ccompat-open-target")) {
            alice().contentType(CT_JSON).body(createBody(target, "AVRO", PRIVATE_AVRO, List.of()))
                    .post("/registry/v3/groups/default/artifacts").then().statusCode(200);
        }
        String wrapper = "{\"type\":\"record\",\"name\":\"Public\",\"fields\":[{\"name\":\"s\",\"type\":\"Secret\"}]}";
        bob().contentType(CT_JSON).header("X-Registry-GroupId", GROUP)
                .body(Map.of("schema", wrapper, "references",
                        List.of(Map.of("name", "Secret", "subject", "ccompat-private-target", "version", 1))))
                .post("/ccompat/v7/subjects/bob-ccompat-wrapper/versions").then().statusCode(403);
        alice().get(path(GROUP, "bob-ccompat-wrapper")).then().statusCode(404);
        alice().get(path("default", "bob-ccompat-wrapper")).then().statusCode(404);

        bob().contentType(CT_JSON).header("X-Registry-GroupId", GROUP)
                .body(Map.of("schema", wrapper, "references",
                        List.of(Map.of("name", "Secret", "subject", "ccompat-open-target", "version", 1))))
                .post("/ccompat/v7/subjects/bob-ccompat-open-wrapper/versions").then().statusCode(200);
        assertEquals(1, versionCount(GROUP, "bob-ccompat-open-wrapper"));
    }

    @Test
    public void ccompatReferencedByListsOnlyReadableReferrers() {
        create(alice(), "ref-target", "AVRO", OPEN_AVRO.replace("Open", "Target"), List.of(), 200);
        String referrer = "{\"type\":\"record\",\"name\":\"%s\",\"fields\":[{\"name\":\"t\",\"type\":\"Target\"}]}";
        create(alice(), "private-referrer", "AVRO", referrer.formatted("Hidden"),
                List.of(reference("ref-target", "Target")), 200);
        create(alice(), "public-referrer", "AVRO", referrer.formatted("Shown"),
                List.of(reference("ref-target", "Target")), 200);
        int visible = (int) versionMetaData("public-referrer", "contentId");
        int hidden = (int) versionMetaData("private-referrer", "contentId");

        String referencedBy = "/ccompat/v7/subjects/ref-target/versions/1/referencedby";
        bob().header("X-Registry-GroupId", GROUP).get(referencedBy).then().statusCode(200)
                .body("", equalTo(List.of(visible)));
        alice().header("X-Registry-GroupId", GROUP).get(referencedBy).then().statusCode(200)
                .body("size()", equalTo(2)).body("", hasItems(visible, hidden));
    }

    // ==================== Artifacts written by write hooks ====================

    private static final String MODEL = """
            {"name": "Model", "input": {"type": "object", "properties": {"p": {"type": "%s"}}}}""";

    @Test
    public void embeddedSchemaExtractionRequiresWriteOnTheSchemaArtifact() {
        create(bob(), "denied-model", "MODEL_SCHEMA", MODEL.formatted("string"), List.of(), 403);
        alice().get(path(GROUP, "denied-model")).then().statusCode(404);
        alice().get(path(GROUP, "denied-model-input-schema")).then().statusCode(404);

        create(bob(), "free-model", "MODEL_SCHEMA", MODEL.formatted("string"), List.of(), 200);
        assertEquals(1, versionCount(GROUP, "free-model-input-schema"));
    }

    @Test
    public void embeddedSchemaExtractionCannotAddVersionsToADeniedSchemaArtifact() {
        // The admin's model owns the denied schema artifact; bob may write the model itself
        create(alice(), "locked-model", "MODEL_SCHEMA", MODEL.formatted("string"), List.of(), 200);
        assertEquals(1, versionCount(GROUP, "locked-model-input-schema"));

        bob().contentType(CT_JSON)
                .body(Map.of("content", Map.of("content", MODEL.formatted("integer"), "contentType", CT_JSON)))
                .post(path(GROUP, "locked-model") + "/versions").then().statusCode(403);

        assertEquals(1, versionCount(GROUP, "locked-model-input-schema"));
        assertEquals(1, versionCount(GROUP, "locked-model"));
    }

    private static final String OPENAPI = """
            {"openapi":"3.0.0","info":{"title":"Api","description":"Grants","version":"1.0.0",
            "x-agent-card":{"capabilities":{},"defaultInputModes":["text"],"defaultOutputModes":["text"],
            "skills":[{"id":"s","name":"S","description":"S","tags":["s"]}]}},
            "servers":[{"url":"https://example.com"}],"paths":{}}""";

    @Test
    public void agentCardGenerationRequiresWriteOnTheCompanionArtifact() {
        create(bob(), "denied-api", "OPENAPI", OPENAPI, List.of(), 403);
        alice().get(path(GROUP, "denied-api")).then().statusCode(404);
        alice().get(path(GROUP, "denied-api-agent-card")).then().statusCode(404);

        create(bob(), "free-api", "OPENAPI", OPENAPI, List.of(), 200);
        alice().get(path(GROUP, "free-api-agent-card")).then().statusCode(200)
                .body("artifactType", equalTo("AGENT_CARD"));
    }

    private static final String PLAIN_OPENAPI = """
            {"openapi":"3.0.0","info":{"title":"Api","description":"Grants","version":"1.0.0"},
            "servers":[{"url":"https://example.com"}],"paths":{}}""";

    private static void addVersion(RequestSpecification as, String group, String artifactId, String content,
            List<Map<String, Object>> references, boolean draft, int expectedStatus) {
        as.contentType(CT_JSON).body(Map.of("isDraft", draft, "content", Map.of("content", content,
                        "contentType", CT_JSON, "references", references)))
                .post(path(group, artifactId) + "/versions").then().statusCode(expectedStatus);
    }

    private static void createIn(String group, String artifactId, String type, String content) {
        alice().contentType(CT_JSON).body(createBody(artifactId, type, content, List.of()))
                .post("/registry/v3/groups/" + group + "/artifacts").then().statusCode(200);
    }

    @Test
    public void generatingANewArtifactRequiresWriteOnItsGroup() {
        // bob may write every artifact in the locked group, but not the group itself, so he may update
        // existing artifacts there but not create new ones, including generated ones
        createIn(GENERATED_LOCKED_GROUP, "model", "MODEL_SCHEMA", "{\"name\": \"Model\"}");
        createIn(GENERATED_LOCKED_GROUP, "api", "OPENAPI", PLAIN_OPENAPI);

        addVersion(bob(), GENERATED_LOCKED_GROUP, "model", MODEL.formatted("string"), List.of(), false, 403);
        addVersion(bob(), GENERATED_LOCKED_GROUP, "api", OPENAPI, List.of(), false, 403);
        alice().get(path(GENERATED_LOCKED_GROUP, "model-input-schema")).then().statusCode(404);
        alice().get(path(GENERATED_LOCKED_GROUP, "api-agent-card")).then().statusCode(404);
        assertEquals(1, versionCount(GENERATED_LOCKED_GROUP, "model"));
        assertEquals(1, versionCount(GENERATED_LOCKED_GROUP, "api"));

        // An update that generates nothing is still allowed
        addVersion(bob(), GENERATED_LOCKED_GROUP, "model", "{\"name\": \"Renamed\"}", List.of(), false, 200);

        // Where bob may write the group, the same updates generate the artifacts
        createIn(GROUP, "open-parent-model", "MODEL_SCHEMA", "{\"name\": \"Model\"}");
        createIn(GROUP, "open-parent-api", "OPENAPI", PLAIN_OPENAPI);
        addVersion(bob(), GROUP, "open-parent-model", MODEL.formatted("string"), List.of(), false, 200);
        addVersion(bob(), GROUP, "open-parent-api", OPENAPI, List.of(), false, 200);
        assertEquals(1, versionCount(GROUP, "open-parent-model-input-schema"));
        assertEquals(1, versionCount(GROUP, "open-parent-api-agent-card"));
    }

    @Test
    public void aRequestRejectedForItsReferencesLeavesNoGeneratedWrites() {
        create(alice(), "private-atomic-target", "AVRO", PRIVATE_AVRO, List.of(), 200);
        createIn(GROUP, "atomic-model-input-schema", "JSON", "{\"type\": \"string\"}");
        List<Map<String, Object>> unreadable = List.of(reference("private-atomic-target", "Secret"));

        for (String type : List.of("integer", "boolean", "number")) {
            create(bob(), "atomic-model", "MODEL_SCHEMA", MODEL.formatted(type), unreadable, 403);
        }
        alice().get(path(GROUP, "atomic-model")).then().statusCode(404);
        assertEquals(1, versionCount(GROUP, "atomic-model-input-schema"));

        createIn(GROUP, "atomic-parent", "MODEL_SCHEMA", "{\"name\": \"Model\"}");
        addVersion(bob(), GROUP, "atomic-parent", MODEL.formatted("integer"), unreadable, false, 403);
        alice().get(path(GROUP, "atomic-parent-input-schema")).then().statusCode(404);
        assertEquals(1, versionCount(GROUP, "atomic-parent"));
    }

    @Test
    public void draftVersionsRequireReadOnTheirReferences() {
        create(alice(), "private-draft-target", "AVRO", PRIVATE_AVRO, List.of(), 200);
        create(alice(), "open-draft-target", "AVRO", OPEN_AVRO, List.of(), 200);
        createIn(GROUP, "draft-parent", "JSON", "{\"type\": \"object\"}");

        addVersion(bob(), GROUP, "draft-parent", "{\"type\": \"object\", \"v\": 2}",
                List.of(reference("private-draft-target", "Secret")), true, 403);
        assertEquals(1, versionCount(GROUP, "draft-parent"));

        addVersion(bob(), GROUP, "draft-parent", "{\"type\": \"object\", \"v\": 3}",
                List.of(reference("open-draft-target", "Open")), true, 200);
        assertEquals(2, versionCount(GROUP, "draft-parent"));
    }

    // ==================== Creation requires write on the group ====================

    @Test
    public void ccompatRegistrationOfANewSubjectRequiresWriteOnTheGroup() {
        bob().contentType(CT_JSON).header("X-Registry-GroupId", LOCKED_GROUP).body(Map.of("schema", OPEN_AVRO))
                .post("/ccompat/v7/subjects/new-subject/versions").then().statusCode(403);
        alice().get(path(LOCKED_GROUP, "new-subject")).then().statusCode(404);

        bob().contentType(CT_JSON).header("X-Registry-GroupId", GROUP).body(Map.of("schema", OPEN_AVRO))
                .post("/ccompat/v7/subjects/new-subject/versions").then().statusCode(200);
        assertEquals(1, versionCount(GROUP, "new-subject"));
    }

    @Test
    public void mcpPublishingANewServerRequiresWriteOnTheGroup() {
        bob().contentType(CT_JSON)
                .body(Map.of("name", LOCKED_MCP_NAMESPACE + "/weather", "version", "1.0.0", "description", "d"))
                .post("/mcp-registry/v0.1/publish").then().statusCode(403);
        alice().get(path(LOCKED_MCP_NAMESPACE, "weather")).then().statusCode(404);

        // Same artifact grant, plus group write: allowed, and stored under the expected name
        bob().contentType(CT_JSON)
                .body(Map.of("name", OPEN_MCP_NAMESPACE + "/weather", "version", "1.0.0", "description", "d"))
                .post("/mcp-registry/v0.1/publish").then().statusCode(200);
        assertEquals(1, versionCount(OPEN_MCP_NAMESPACE, "weather"));
    }

    @Test
    public void icebergRenameRequiresWriteOnTheDestinationNamespace() {
        for (String namespace : List.of(ICEBERG_SOURCE, LOCKED_GROUP)) {
            alice().contentType(CT_JSON).body(Map.of("namespace", List.of(namespace)))
                    .post("/iceberg/v1/default/namespaces").then().statusCode(200);
        }
        alice().contentType(CT_JSON).body(Map.of("name", "table", "schema", Map.of("type", "struct",
                "schema-id", 0, "fields", List.of(Map.of("id", 1, "name", "id", "required", true, "type", "long")))))
                .post("/iceberg/v1/default/namespaces/" + ICEBERG_SOURCE + "/tables").then().statusCode(200);

        bob().contentType(CT_JSON).body(rename(LOCKED_GROUP, "renamed"))
                .post("/iceberg/v1/default/tables/rename").then().statusCode(403)
                .body("error.code", equalTo(403)).body("error.type", equalTo("ForbiddenException"));
        alice().get(path(ICEBERG_SOURCE, "table")).then().statusCode(200);
        alice().get(path(LOCKED_GROUP, "renamed")).then().statusCode(404);

        bob().contentType(CT_JSON).body(rename(ICEBERG_SOURCE, "renamed"))
                .post("/iceberg/v1/default/tables/rename").then().statusCode(204);
        alice().get(path(ICEBERG_SOURCE, "renamed")).then().statusCode(200);
    }

    private static Map<String, Object> rename(String destinationNamespace, String destinationName) {
        return Map.of("source", Map.of("namespace", List.of(ICEBERG_SOURCE), "name", "table"),
                "destination", Map.of("namespace", List.of(destinationNamespace), "name", destinationName));
    }
}
