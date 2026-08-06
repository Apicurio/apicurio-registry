package io.apicurio.registry.rules.compatibility;

import org.junit.jupiter.api.Test;

import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.readResource;
import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.throwOnFailure;

class AgentCardCompatibilityCheckerSmokeTest {

    @Test
    void testCompatibility() throws Exception {
        var executor = new CompatibilityTestExecutor(new AgentCardCompatibilityChecker());
        throwOnFailure(executor.execute(readResource(this.getClass(), "agent-card-compatibility-test-data.json")));
    }
}
