package io.apicurio.registry.auth.grants;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * Server-side reference matching ({@code autoReferences}) with per-resource authorization: artifacts the
 * caller cannot read must never be matched, and must not be disclosed in the error.
 */
@QuarkusTest
@TestProfile(GrantsAuthorizationTest.GrantsTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class GrantsAutoReferencesTest extends AbstractResourceTestBase {

    private static final String TEAM_A = "grants-team-a";

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl)
                .vertx(vertx)
                .basicAuth("alice", "alice"));
    }

    /** Test users use their username as password. */
    private static RequestSpecification as(String user) {
        return given().auth().preemptive().basic(user, user);
    }

    @Test
    public void readableDependencyIsMatched() throws Exception {
        String ns = uniqueNamespace();
        createArtifact(TEAM_A, ns + ".Address", ArtifactType.AVRO, record(ns, "Address", "[]"),
                ContentTypes.APPLICATION_JSON);

        as("bob1").contentType(CT_JSON).body(autoCreate(ns + ".Order", ns))
                .post("/registry/v3/groups/" + TEAM_A + "/artifacts").then().statusCode(200);

        List<Map<String, Object>> refs = as("alice")
                .get("/registry/v3/groups/" + TEAM_A + "/artifacts/" + ns + ".Order/versions/1/references")
                .then().statusCode(200).extract().jsonPath().getList("$");
        Assertions.assertEquals(List.of(Map.of("name", ns + ".Address", "groupId", TEAM_A,
                "artifactId", ns + ".Address", "version", "1")), refs);
    }

    @Test
    public void unreadableDependencyIsNotMatchedOrDisclosed() throws Exception {
        String group = "grants-autoref";
        String invoiceId = "invoice-" + UUID.randomUUID();
        String hiddenArtifactId = "hidden-money-" + UUID.randomUUID();
        String schemaId = "https://ex.com/" + UUID.randomUUID() + "/money.json";
        createArtifact(group, hiddenArtifactId, ArtifactType.JSON,
                "{\"$id\":\"" + schemaId + "\",\"type\":\"object\"}", ContentTypes.APPLICATION_JSON);
        String content = "{\"type\":\"object\",\"properties\":{\"m\":{\"$ref\":\"" + schemaId + "\"}}}";
        Map<String, Object> request = Map.of("artifactId", invoiceId, "artifactType", ArtifactType.JSON,
                "firstVersion", Map.of("autoReferences", true,
                        "content", Map.of("content", content, "contentType", ContentTypes.APPLICATION_JSON)));

        String body = as("bob1").contentType(CT_JSON).body(request)
                .post("/registry/v3/groups/" + group + "/artifacts").then().statusCode(400)
                .extract().asString();

        JsonPath error = JsonPath.from(body);
        Assertions.assertEquals("ReferenceAutoMatchException", error.getString("name"));
        Assertions.assertEquals(List.of(schemaId), error.getList("causes.context"));
        Assertions.assertEquals(List.of("No matching artifact found in group '" + group + "'."),
                error.getList("causes.description"));
        String disclosed = error.getString("title") + "|" + error.getString("detail") + "|"
                + error.getList("causes");
        Assertions.assertFalse(disclosed.contains(hiddenArtifactId), disclosed);
        Assertions.assertFalse(body.contains(hiddenArtifactId), body);
        // Nothing was created for the failed request
        as("alice").get("/registry/v3/groups/" + group + "/artifacts/" + invoiceId).then().statusCode(404);
    }

    private static String uniqueNamespace() {
        return "com.acme.g" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String record(String ns, String name, String fields) {
        return "{\"type\":\"record\",\"namespace\":\"" + ns + "\",\"name\":\"" + name + "\",\"fields\":"
                + fields + "}";
    }

    private static Map<String, Object> autoCreate(String artifactId, String ns) {
        String content = record(ns, "Order", "[{\"name\":\"a\",\"type\":\"" + ns + ".Address\"}]");
        return Map.of("artifactId", artifactId, "artifactType", ArtifactType.AVRO,
                "firstVersion", Map.of("autoReferences", true,
                        "content", Map.of("content", content, "contentType", ContentTypes.APPLICATION_JSON)));
    }
}
