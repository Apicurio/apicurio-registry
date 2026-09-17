package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.apicurio.registry.noprofile.mcpregistry.rest.v0.McpRegistryRequests.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@QuarkusTest
@TestProfile(McpRegistryExperimentalFeaturesProfile.class)
class McpRegistryGovernanceTest extends AbstractResourceTestBase {

    private static final String BASE = "/mcp-registry/v0.1";

    @InjectMock
    RulesService rules;

    private String namespace() {
        return "io.github.governance" + UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    void configuredRulesRunForCreateAndUpdateAndRejectBeforeWriting() {
        String ns = namespace();
        Map<String, String> server = Map.of("name", ns + "/server", "version", "1.0.0",
                "description", "Test server");
        given().contentType(CT_JSON).body(server).post(BASE + "/publish").then().statusCode(200);
        verify(rules).applyRules(eq(ns), eq("server"), eq("MCP_SERVER"), any(),
                eq(RuleApplicationType.CREATE), eq(List.of()), eq(Map.of()));

        doThrow(new RuleViolationException("Blocked by configured rule", RuleType.COMPATIBILITY, "BACKWARD",
                Set.of(new RuleViolation("Incompatible test contract", "/version"))))
                .when(rules).applyRules(eq(ns), eq("server"), eq("MCP_SERVER"), any(),
                        eq(RuleApplicationType.UPDATE), any(), any());
        given().contentType(CT_JSON)
                .body(Map.of("name", ns + "/server", "version", "2.0.0", "description", "Updated"))
                .post(BASE + "/publish").then().statusCode(400)
                .body("error", equalTo("Blocked by configured rule"));
        given().get(BASE + "/servers/" + ns + "/server/versions/2.0.0").then().statusCode(404);
        given().get(BASE + "/servers/" + ns + "/server/versions").then().statusCode(200)
                .body("metadata.count", equalTo(1));
    }

    @Test
    void rejectedCreateDoesNotPersistAnArtifact() {
        String ns = namespace();
        doThrow(new RuleViolationException("Create blocked", RuleType.VALIDITY, "FULL",
                Set.of(new RuleViolation("Rejected test definition", "/"))))
                .when(rules).applyRules(eq(ns), eq("server"), eq("MCP_SERVER"), any(),
                        eq(RuleApplicationType.CREATE), any(), any());
        given().contentType(CT_JSON).body(Map.of("name", ns + "/server", "version", "1.0.0",
                "description", "Rejected")).post(BASE + "/publish").then().statusCode(400)
                .body("error", equalTo("Create blocked"));
        given().get("/registry/v3/groups/" + ns + "/artifacts/server").then().statusCode(404);
    }

    @Test
    void mcpEndpointsCannotReadOrModifyAnotherArtifactType() {
        String ns = namespace();
        given().contentType(CT_JSON).body(Map.of(
                "artifactId", "ordinary", "artifactType", "JSON",
                "firstVersion", Map.of("version", "1.0.0", "content", Map.of(
                        "content", "{\"name\":\"not-an-mcp-server\"}", "contentType", CT_JSON))))
                .post("/registry/v3/groups/" + ns + "/artifacts").then().statusCode(200);

        String path = BASE + "/servers/" + ns + "/ordinary";
        for (String suffix : List.of("", "/versions", "/versions/1.0.0")) {
            given().get(path + suffix).then().statusCode(404);
        }
        for (String suffix : List.of("/status", "/versions/1.0.0/status")) {
            given().contentType(CT_JSON).body(Map.of("status", "deprecated"))
                    .patch(path + suffix).then().statusCode(404);
        }
        given().delete(path + "/versions/1.0.0").then().statusCode(404);
        given().contentType(CT_JSON).body(Map.of("name", ns + "/ordinary", "version", "2.0.0",
                "description", "Collision")).post(BASE + "/publish").then().statusCode(404);
        given().get("/registry/v3/groups/" + ns + "/artifacts/ordinary/versions/1.0.0")
                .then().statusCode(200).body("state", equalTo("ENABLED"));
        given().get("/registry/v3/groups/" + ns + "/artifacts/ordinary/versions/2.0.0")
                .then().statusCode(404);
    }
}
