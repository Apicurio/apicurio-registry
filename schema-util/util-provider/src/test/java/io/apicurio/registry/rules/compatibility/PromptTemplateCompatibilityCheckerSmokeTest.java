package io.apicurio.registry.rules.compatibility;

import org.junit.jupiter.api.Test;

import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.readResource;
import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.throwOnFailure;

class PromptTemplateCompatibilityCheckerSmokeTest {

    @Test
    void testCompatibility() throws Exception {
        var executor = new CompatibilityTestExecutor(new PromptTemplateCompatibilityChecker());
        throwOnFailure(executor.execute(readResource(this.getClass(), "prompt-template-compatibility-test-data.json")));
    }
}
