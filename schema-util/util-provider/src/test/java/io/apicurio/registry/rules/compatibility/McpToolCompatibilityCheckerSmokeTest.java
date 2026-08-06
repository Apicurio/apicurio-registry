package io.apicurio.registry.rules.compatibility;

import org.junit.jupiter.api.Test;

import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.readResource;
import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.throwOnFailure;

class McpToolCompatibilityCheckerSmokeTest {

    @Test
    void testCompatibility() throws Exception {
        var executor = new CompatibilityTestExecutor(new McpToolCompatibilityChecker());
        throwOnFailure(executor.execute(readResource(this.getClass(), "mcp-tool-compatibility-test-data.json")));
    }
}
