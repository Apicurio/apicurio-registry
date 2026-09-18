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

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.apicurio.registry.noprofile.mcpregistry.rest.v0.McpRegistryRequests.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

@QuarkusTest
@TestProfile(McpRegistryLegacyErrorCodesProfile.class)
class McpRegistryLegacyErrorCodesTest extends AbstractResourceTestBase {

    private static final String BASE = "/mcp-registry/v0.1";

    @InjectMock
    RulesService rules;

    @Test
    void ruleViolationsStay400WhenLegacyErrorCodesAreEnabled() {
        // Without the catch around applyRules this answers 409 here and 400 elsewhere: the same rejected
        // publish would carry a different status depending on a v2 compatibility switch.
        String ns = "io.github.legacy" + UUID.randomUUID().toString().replace("-", "");
        doThrow(new RuleViolationException("Blocked by configured rule", RuleType.VALIDITY, "FULL",
                Set.of(new RuleViolation("Rejected test definition", "/"))))
                .when(rules).applyRules(eq(ns), eq("server"), eq("MCP_SERVER"), any(),
                        eq(RuleApplicationType.CREATE), any(), any());

        given().contentType(CT_JSON)
                .body(Map.of("name", ns + "/server", "version", "1.0.0", "description", "Rejected"))
                .post(BASE + "/publish")
                .then()
                .statusCode(400)
                .body("error", equalTo("Blocked by configured rule: Rejected test definition"));
    }
}
