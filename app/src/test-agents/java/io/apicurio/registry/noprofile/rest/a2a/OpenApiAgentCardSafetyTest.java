package io.apicurio.registry.noprofile.rest.a2a;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.auth.A2AAuthTestProfile;
import io.apicurio.registry.a2a.openapi.OpenApiAgentCardAssembler;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/** Authenticated regressions for generated companion governance and lifecycle. */
@QuarkusTest
@TestProfile(OpenApiAgentCardSafetyTest.Profile.class)
class OpenApiAgentCardSafetyTest extends AbstractResourceTestBase {
    public static class Profile extends A2AAuthTestProfile {
        @Override
        public Map<String,String> getConfigOverrides() {
            var config = new HashMap<>(super.getConfigOverrides());
            config.put("apicurio.a2a.openapi-integration.enabled", "true");
            config.put("apicurio.rest.mutability.artifact-version-content.enabled", "true");
            return config;
        }
    }

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create().registryUrl(registryV3ApiUrl)
                .vertx(vertx).basicAuth("alice", "alice"));
    }

    private String group() { return "probe-" + UUID.randomUUID(); }
    private String path(String group, String id) { return "/registry/v3/groups/" + group + "/artifacts/" + id; }
    private String document(String name, boolean secondSkill) {
        return """
                {"openapi":"3.0.0","info":{"title":"%s","description":"Weather service","version":"1.0.0",
                "x-agent-card":{"capabilities":{},"defaultInputModes":["text"],"defaultOutputModes":["text"],
                "skills":[{"id":"weather","name":"Weather","description":"Forecast","tags":["weather"]}%s]}},
                "servers":[{"url":"https://example.com"}],"paths":{}}
                """.formatted(name, secondSkill ? ",{\"id\":\"extra\",\"name\":\"Extra\",\"description\":\"Extra\",\"tags\":[\"extra\"]}" : "");
    }
    private void create(String user, String group, String id, String type, String content, boolean draft, Map<String,String> labels) {
        given().auth().preemptive().basic(user,user).contentType(CT_JSON)
                .body(Map.of("artifactId",id,"artifactType",type,"labels",labels,"firstVersion",
                        Map.of("version","1","isDraft",draft,"content",Map.of("content",content,"contentType",CT_JSON))))
                .post("/registry/v3/groups/" + group + "/artifacts").then().statusCode(200);
    }
    private void update(String user, String group, String id, String content, int status) {
        given().auth().preemptive().basic(user,user).contentType(CT_JSON)
                .body(Map.of("content",Map.of("content",content,"contentType",CT_JSON)))
                .post(path(group,id) + "/versions").then().statusCode(status);
    }
    private String card(String group, String id) {
        return given().auth().preemptive().basic("alice","alice").get(path(group,id)+"/versions/branch=latest/content")
                .then().statusCode(200).extract().asString();
    }

    @Test void draftSourceDoesNotPublishCompanion() {
        String g=group();
        create("alice",g,"api","OPENAPI",document("Draft",false),true,Map.of());
        given().auth().preemptive().basic("alice","alice").get(path(g,"api")+"/versions/1")
                .then().statusCode(200).body("state",equalTo("DRAFT"));
        given().auth().preemptive().basic("alice","alice").get(path(g,"api-agent-card")+"/versions/1")
                .then().statusCode(404);
    }

    @Test void generationEnforcesCompanionCompatibility() {
        String g=group();
        create("alice",g,"api","OPENAPI",document("Initial",true),false,Map.of());
        given().auth().preemptive().basic("alice","alice").contentType(CT_JSON)
                .body(Map.of("ruleType","COMPATIBILITY","config","BACKWARD"))
                .post(path(g,"api-agent-card")+"/rules").then().statusCode(204);
        String smaller;
        try { smaller = new OpenApiAgentCardAssembler().assemble(
                TypedContent.create(document("Changed",false),CT_JSON)); }
        catch (Exception e) { throw new RuntimeException(e); }
        update("alice",g,"api-agent-card",smaller,400);
        update("alice",g,"api",document("Changed",false),200);
        given().auth().preemptive().basic("alice","alice").get(path(g,"api-agent-card")+"/versions/branch=latest/content")
                .then().statusCode(200).body("skills.size()",equalTo(2));
    }

    @Test void recreatedSourceCannotWriteAnotherOwnersCompanion() {
        String g=group();
        create("alice",g,"api","OPENAPI",document("Alice",false),false,Map.of());
        String original=card(g,"api-agent-card");
        given().auth().preemptive().basic("alice","alice").delete(path(g,"api")).then().statusCode(204);
        update("bob1",g,"api-agent-card",original,403);
        create("bob1",g,"api","OPENAPI",document("Bob",false),false,Map.of());
        given().auth().preemptive().basic("alice","alice").get(path(g,"api-agent-card")+"/versions/branch=latest/content")
                .then().statusCode(200).body("name",equalTo("Alice"));
    }

    @Test void generationPreservesUnrelatedSourceLabels() {
        String g=group();
        create("alice",g,"api","OPENAPI",document("Labels",false),false,Map.of("CaseKey","MiXeD".repeat(200)));
        given().auth().preemptive().basic("alice","alice").get(path(g,"api"))
                .then().statusCode(200).body("labels.CaseKey",equalTo("MiXeD".repeat(200)))
                .body("labels.'apicurio.a2a.openapi-agent-card.artifact-id'",equalTo("api-agent-card"));
    }

    @Test void missingHashDoesNotDisableManualEditProtection() {
        String g=group();
        create("alice",g,"api","OPENAPI",document("Initial",false),false,Map.of());
        String manual=card(g,"api-agent-card").replace("Initial","Human edit");
        update("alice",g,"api-agent-card",manual,200);
        String prefix="apicurio.a2a.openapi-agent-card.";
        given().auth().preemptive().basic("alice","alice").contentType(CT_JSON)
                .body(Map.of("labels",Map.of(prefix+"generated","true",prefix+"source-group-id",g,
                        prefix+"source-artifact-id","api")))
                .put(path(g,"api-agent-card")).then().statusCode(204);
        update("alice",g,"api",document("Generated again",false),200);
        given().auth().preemptive().basic("alice","alice").get(path(g,"api-agent-card")+"/versions/branch=latest/content")
                .then().statusCode(200).body("name",equalTo("Human edit"));
    }

    @Test void draftPublicationUsesEditedContent() {
        String g=group();
        create("alice",g,"api","OPENAPI",document("Old draft",false),true,Map.of());
        given().auth().preemptive().basic("alice","alice").contentType(CT_JSON)
                .body(Map.of("content",document("Published content",false),"contentType",CT_JSON))
                .put(path(g,"api")+"/versions/1/content").then().statusCode(204);
        given().auth().preemptive().basic("alice","alice").contentType(CT_JSON)
                .body(Map.of("state","ENABLED")).put(path(g,"api")+"/versions/1/state").then().statusCode(204);
        given().auth().preemptive().basic("alice","alice").get(path(g,"api-agent-card")+"/versions/branch=latest/content")
                .then().statusCode(200).body("name",equalTo("Published content"));
    }

    @Test void disabledGeneratedTipDoesNotPermanentlyBlockSynchronization() {
        String g=group();
        create("bob1",g,"api","OPENAPI",document("Initial",false),false,Map.of());
        update("bob1",g,"api",document("Second",false),200);
        given().auth().preemptive().basic("bob1","bob1").contentType(CT_JSON)
                .body(Map.of("state","DISABLED")).put(path(g,"api-agent-card")+"/versions/2/state")
                .then().statusCode(204);
        update("bob1",g,"api",document("Third",false),200);
        given().auth().preemptive().basic("bob1","bob1").get(path(g,"api-agent-card")+"/versions/branch=latest/content")
                .then().statusCode(200).body("name",equalTo("Third"));
    }

    @Test void invalidDraftPublicationLeavesDraftAndNoCompanion() {
        String g=group();
        String malformed = document("Draft",false).replace("\"capabilities\":{}", "\"capabilities\":42");
        create("alice",g,"api","OPENAPI",malformed,true,Map.of());
        given().auth().preemptive().basic("alice","alice").contentType(CT_JSON)
                .body(Map.of("state","ENABLED")).put(path(g,"api")+"/versions/1/state")
                .then().statusCode(400);
        given().auth().preemptive().basic("alice","alice").get(path(g,"api")+"/versions/1")
                .then().statusCode(200).body("state",equalTo("DRAFT"));
        given().auth().preemptive().basic("alice","alice").get(path(g,"api-agent-card"))
                .then().statusCode(404);
    }

    @Test void draftPublicationDryRunDoesNotCreateCompanion() {
        String g=group();
        create("alice",g,"api","OPENAPI",document("Draft",false),true,Map.of());
        given().auth().preemptive().basic("alice","alice").contentType(CT_JSON)
                .queryParam("dryRun",true).body(Map.of("state","ENABLED"))
                .put(path(g,"api")+"/versions/1/state").then().statusCode(204);
        given().auth().preemptive().basic("alice","alice").get(path(g,"api-agent-card"))
                .then().statusCode(404);
        given().auth().preemptive().basic("alice","alice").get(path(g,"api")+"/versions/1")
                .then().statusCode(200).body("state",equalTo("DRAFT"));
    }
}
