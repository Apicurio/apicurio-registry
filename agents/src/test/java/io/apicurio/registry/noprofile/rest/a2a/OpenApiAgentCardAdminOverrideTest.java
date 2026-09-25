package io.apicurio.registry.noprofile.rest.a2a;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(OpenApiAgentCardAdminOverrideTest.Profile.class)
class OpenApiAgentCardAdminOverrideTest extends AbstractResourceTestBase {
    public static class Profile extends OpenApiAgentCardSafetyTest.Profile {
        @Override
        public Map<String, String> getConfigOverrides() {
            var config = super.getConfigOverrides();
            config.put("apicurio.auth.role-based-authorization", "false");
            config.put("apicurio.auth.admin-override.type", "user");
            config.put("apicurio.auth.admin-override.user", "alice");
            config.put("quarkus.security.users.embedded.roles.alice", "sr-developer");
            return config;
        }
    }

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create().registryUrl(registryV3ApiUrl)
                .vertx(vertx).basicAuth("alice", "alice"));
    }

    @Test
    void userOverrideCanSynchronizeWithoutRbacAdminRole() {
        String group = "override-" + UUID.randomUUID();
        String doc = """
                {"openapi":"3.0.0","info":{"title":"Original","description":"Weather","version":"1",
                 "x-agent-card":{"capabilities":{},"skills":[{"id":"one","name":"One","description":"One","tags":["one"]}],
                 "defaultInputModes":["text"],"defaultOutputModes":["text"]}},
                 "servers":[{"url":"https://example.com"}],"paths":{}}
                """;
        String path = "/registry/v3/groups/" + group + "/artifacts";
        given().auth().preemptive().basic("bob1", "bob1").contentType(CT_JSON)
                .body(Map.of("artifactId","api","artifactType","OPENAPI","firstVersion",Map.of("content",
                        Map.of("content",doc,"contentType",CT_JSON))))
                .post(path).then().statusCode(200);
        given().auth().preemptive().basic("alice","alice").contentType(CT_JSON)
                .body(Map.of("content",Map.of("content",doc.replace("Original","Updated"),"contentType",CT_JSON)))
                .post(path + "/api/versions").then().statusCode(200);
        given().auth().preemptive().basic("alice","alice")
                .get(path + "/api-agent-card/versions/branch=latest/content").then().statusCode(200)
                .body("name",equalTo("Updated"));
    }
}
